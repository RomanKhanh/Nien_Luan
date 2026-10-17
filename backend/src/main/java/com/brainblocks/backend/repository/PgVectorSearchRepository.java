package com.brainblocks.backend.repository;

import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.util.VectorMath;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;

/**
 * Truy vấn pgvector theo khuyến nghị để dùng được index HNSW: CTE MATERIALIZED chỉ
 * {@code ORDER BY embedding <=> :q LIMIT :k} (tăng dần, trực tiếp trên toán tử), ngưỡng khoảng cách lọc ở ngoài CTE.
 */
@Repository
public class PgVectorSearchRepository implements VectorSearchRepository {
    private static final String NEAREST_CHUNKS = """
            WITH nearest AS MATERIALIZED (
                SELECT c.id, c.knowledge_document_id, c.content, c.embedding <=> CAST(:query AS vector) AS distance
                FROM document_chunks c
                JOIN knowledge_documents d ON d.id = c.knowledge_document_id
                WHERE d.status = :status AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> CAST(:query AS vector)
                LIMIT :limit
            )
            SELECT n.id, n.knowledge_document_id, d.title, n.content, n.distance
            FROM nearest n
            JOIN knowledge_documents d ON d.id = n.knowledge_document_id
            WHERE n.distance <= :maxDistance
            ORDER BY n.distance, n.id
            """;

    // age NULL thì bỏ điều kiện tuổi; excludedIds rỗng được thay bằng {-1} vì NOT IN () không hợp lệ
    private static final String NEAREST_PRODUCTS = """
            WITH nearest AS MATERIALIZED (
                SELECT e.product_id, e.embedding <=> CAST(:query AS vector) AS distance
                FROM product_embeddings e
                JOIN products p ON p.id = e.product_id
                WHERE p.active = TRUE AND p.stock_quantity > 0
                  AND (CAST(:age AS integer) IS NULL OR (p.min_age <= :age AND p.max_age >= :age))
                  AND p.id NOT IN (:excludedIds)
                ORDER BY e.embedding <=> CAST(:query AS vector)
                LIMIT :limit
            )
            SELECT product_id, distance FROM nearest
            WHERE distance <= :maxDistance
            ORDER BY distance, product_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PgVectorSearchRepository(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.tx.setReadOnly(true);
        // transaction riêng: SET LOCAL không lan sang transaction của người gọi, và một lỗi SQL ở đây không làm
        // hỏng transaction đang mở của ChatService (PostgreSQL hủy cả transaction khi một câu lệnh lỗi)
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public List<ChunkMatch> nearestChunks(float[] query, int limit, double maxDistance) {
        var params = new MapSqlParameterSource("query", VectorMath.toLiteral(query))
                .addValue("status", DocumentStatus.INDEXED.name())
                .addValue("limit", limit)
                .addValue("maxDistance", maxDistance);
        return inSearchTransaction(() -> jdbc.query(NEAREST_CHUNKS, params, (rs, i) -> new ChunkMatch(
                rs.getLong("id"), rs.getLong("knowledge_document_id"), rs.getString("title"),
                rs.getString("content"), rs.getDouble("distance"))));
    }

    @Override
    public List<ProductMatch> nearestProducts(float[] query, Integer age, Collection<Long> excludedIds, int limit, double maxDistance) {
        var params = new MapSqlParameterSource("query", VectorMath.toLiteral(query))
                .addValue("age", age, java.sql.Types.INTEGER)
                .addValue("excludedIds", excludedIds == null || excludedIds.isEmpty() ? List.of(-1L) : List.copyOf(excludedIds))
                .addValue("limit", limit)
                .addValue("maxDistance", maxDistance);
        return inSearchTransaction(() -> jdbc.query(NEAREST_PRODUCTS, params,
                (rs, i) -> new ProductMatch(rs.getLong("product_id"), rs.getDouble("distance"))));
    }

    // Index HNSW lọc điều kiện WHERE SAU khi lấy ứng viên: bật iterative scan (pgvector >= 0.8) để quét tiếp
    // khi nhiều ứng viên bị lọc bỏ, không trả thiếu kết quả. SET LOCAL chỉ có hiệu lực trong transaction này.
    private <T> T inSearchTransaction(java.util.function.Supplier<T> search) {
        return tx.execute(status -> {
            jdbc.getJdbcTemplate().queryForObject("SELECT set_config('hnsw.iterative_scan', 'strict_order', true)", String.class);
            return search.get();
        });
    }
}
