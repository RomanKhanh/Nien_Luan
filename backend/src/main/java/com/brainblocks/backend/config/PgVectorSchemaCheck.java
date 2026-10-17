package com.brainblocks.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * Kiểm tra pgvector lúc khởi động, sau khi Hibernate cập nhật schema và trước khi nhận request.
 * Với DB cũ (cột embedding kiểu real[]), ddl-auto=update của Hibernate 7 có thử
 * "ALTER COLUMN embedding SET DATA TYPE vector(768)" (giữ dữ liệu, không tạo cột trùng) nhưng chỉ ghi WARN rồi
 * chạy tiếp nếu lệnh lỗi: chưa cài extension vector, hoặc có vector không đúng 768 chiều. Khi đó cột vẫn là real[]
 * và app dừng ở đây với lỗi chỉ tới script migration, thay vì chạy
 * rồi hỏng ở câu hỏi chatbot đầu tiên. Index HNSW được tạo nếu thiếu (IF NOT EXISTS, chạy lại không sao).
 * Chỉ áp dụng cho PostgreSQL; test chạy H2 bỏ qua.
 */
@Slf4j
@Component
public class PgVectorSchemaCheck implements SmartInitializingSingleton {
    static final String DIMENSION_TYPE = "vector(768)";
    static final String HELP = "Run backend/docs/sql/pgvector-migration.sql on this database (see backend/docs/pgvector-setup.md)";
    // bảng, cột, tên index HNSW (cosine)
    private static final List<VectorColumn> COLUMNS = List.of(
            new VectorColumn("document_chunks", "embedding", "idx_document_chunks_embedding_hnsw"),
            new VectorColumn("product_embeddings", "embedding", "idx_product_embeddings_embedding_hnsw"));

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PgVectorSchemaCheck(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!isPostgres()) return;
        List<String> version = jdbc.queryForList("SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        if (version.isEmpty()) {
            throw new IllegalStateException("PostgreSQL extension 'vector' (pgvector) is not installed. " + HELP);
        }
        for (VectorColumn column : COLUMNS) {
            List<String> type = jdbc.queryForList("""
                    SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a
                    WHERE a.attrelid = to_regclass(?) AND a.attname = ? AND NOT a.attisdropped""",
                    String.class, column.table(), column.column());
            if (type.isEmpty() || !DIMENSION_TYPE.equals(type.getFirst())) {
                throw new IllegalStateException("Column %s.%s must be %s but is %s. %s".formatted(column.table(),
                        column.column(), DIMENSION_TYPE, type.isEmpty() ? "missing" : type.getFirst(), HELP));
            }
            ensureIndex(column);
        }
        log.info("pgvector {} ready", version.getFirst());
    }

    private void ensureIndex(VectorColumn column) {
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS " + column.index() + " ON " + column.table()
                    + " USING hnsw (" + column.column() + " vector_cosine_ops)");
        } catch (RuntimeException ex) {
            // Thiếu index chỉ làm truy vấn chậm hơn (quét tuần tự), kết quả vẫn đúng: không dừng app.
            log.warn("Could not create HNSW index {}: {}", column.index(), ex.getMessage());
        }
    }

    private boolean isPostgres() {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not read database metadata", ex);
        }
    }

    private record VectorColumn(String table, String column, String index) {}
}
