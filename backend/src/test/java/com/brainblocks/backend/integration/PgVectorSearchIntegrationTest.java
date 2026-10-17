package com.brainblocks.backend.integration;

import com.brainblocks.backend.config.PgVectorSchemaCheck;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.repository.PgVectorSearchRepository;
import com.brainblocks.backend.repository.VectorSearchRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Chạy câu SQL pgvector thật (toán tử {@code <=>}, CTE MATERIALIZED, index HNSW) trên PostgreSQL có pgvector.
 * KHÔNG chạy trong "mvnw test" mặc định (tag pgvector). Chạy bằng:
 *   PGVECTOR_TEST_URL=jdbc:postgresql://localhost:5432/brainblocks_it PGVECTOR_TEST_PASSWORD=... ./mvnw test -Ppgvector-it
 * Test tạo rồi xóa toàn bộ bảng (create-drop) nên tự từ chối chạy trên DB brainblocks thật.
 */
@Tag("pgvector")
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PgVectorSearchRepository.class, PgVectorSchemaCheck.class})
class PgVectorSearchIntegrationTest {
    static final int DIMENSIONS = 768;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("PGVECTOR_TEST_URL");
        if (url == null || url.isBlank()) throw new IllegalStateException("Set PGVECTOR_TEST_URL to a throwaway PostgreSQL database");
        if (url.split("[?;]", 2)[0].endsWith("/brainblocks")) {
            throw new IllegalStateException("PgVectorSearchIntegrationTest drops its tables: use a separate database, not brainblocks");
        }
        String user = System.getenv().getOrDefault("PGVECTOR_TEST_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("PGVECTOR_TEST_PASSWORD", "");
        // extension phải có trước khi Hibernate tạo cột vector(768)
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired VectorSearchRepository vectorSearch;
    @Autowired PgVectorSchemaCheck schemaCheck;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test
    void schemaHasVectorColumnsAndHnswIndexes() {
        schemaCheck.afterSingletonsInstantiated();
        assertThat(jdbc.queryForObject("SELECT format_type(atttypid, atttypmod) FROM pg_attribute "
                + "WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'", String.class)).isEqualTo("vector(768)");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE indexdef LIKE '%hnsw%vector_cosine_ops%'", String.class))
                .contains("idx_document_chunks_embedding_hnsw", "idx_product_embeddings_embedding_hnsw");
    }

    @Test
    void nearestProductsApplyHardFiltersBeforeRankingByCosineDistance() {
        var tx = new TransactionTemplate(manager);
        var ids = tx.execute(status -> {
            jdbc.execute("DELETE FROM product_embeddings");
            Category category = Category.builder().name("IT " + java.util.UUID.randomUUID()).build();
            em.persist(category);
            var result = new java.util.LinkedHashMap<String, Long>();
            result.put("best", product(category, true, 5, 3, 8, unit(1, 0)));
            result.put("good", product(category, true, 5, 3, 8, unit(0.8, 0.6)));
            result.put("far", product(category, true, 5, 3, 8, unit(0, 1)));
            result.put("inactive", product(category, false, 5, 3, 8, unit(1, 0)));
            result.put("outOfStock", product(category, true, 0, 3, 8, unit(1, 0)));
            result.put("tooOld", product(category, true, 5, 10, 14, unit(1, 0)));
            return result;
        });
        float[] query = unit(1, 0);
        assertThat(vectorSearch.nearestProducts(query, 6, List.of(), 10, 0.45))
                .extracting(VectorSearchRepository.ProductMatch::productId).containsExactly(ids.get("best"), ids.get("good"));
        // không có tuổi: không lọc tuổi
        assertThat(vectorSearch.nearestProducts(query, null, List.of(), 10, 0.45))
                .extracting(VectorSearchRepository.ProductMatch::productId)
                .containsExactlyInAnyOrder(ids.get("best"), ids.get("tooOld"), ids.get("good"));
        // món bé đã có bị loại; limit áp dụng trước ngưỡng
        assertThat(vectorSearch.nearestProducts(query, 6, List.of(ids.get("best")), 1, 0.45))
                .extracting(VectorSearchRepository.ProductMatch::productId).containsExactly(ids.get("good"));
        // xóa sản phẩm thì DB xóa theo vector (ON DELETE CASCADE)
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM product_skill_impacts WHERE product_id = ?", ids.get("far"));
            jdbc.update("DELETE FROM products WHERE id = ?", ids.get("far"));
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embeddings WHERE product_id = ?", Long.class, ids.get("far"))).isZero();
    }

    private Long product(Category category, boolean active, int stock, int minAge, int maxAge, float[] embedding) {
        Product product = Product.builder().name("IT").price(java.math.BigDecimal.TEN).stockQuantity(stock)
                .minAge(minAge).maxAge(maxAge).category(category).active(active)
                .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
        em.persist(product);
        em.persist(ProductEmbedding.builder().product(product).embedding(embedding).contentHash("it").build());
        return product.getId();
    }

    @Test
    void nearestChunksUseCosineDistanceThresholdAndOnlyIndexedDocuments() {
        var tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            jdbc.execute("DELETE FROM document_chunks");
            jdbc.execute("DELETE FROM knowledge_documents");
            Admin admin = Admin.builder().fullName("IT").email(java.util.UUID.randomUUID() + "@it.local").password("x").build();
            em.persist(admin);
            KnowledgeDocument indexed = document(admin, DocumentStatus.INDEXED);
            KnowledgeDocument disabled = document(admin, DocumentStatus.DISABLED);
            chunk(indexed, 0, "giống hệt", unit(1, 0));
            chunk(indexed, 1, "cosine 0.8", unit(0.8, 0.6));
            chunk(indexed, 2, "vuông góc", unit(0, 1));
            chunk(disabled, 0, "tắt", unit(1, 0));
            em.persist(indexed); em.persist(disabled);
        });
        float[] query = unit(1, 0);
        List<VectorSearchRepository.ChunkMatch> found = vectorSearch.nearestChunks(query, 10, 0.4);
        assertThat(found).extracting(VectorSearchRepository.ChunkMatch::content).containsExactly("giống hệt", "cosine 0.8");
        assertThat(found.getFirst().distance()).isCloseTo(0, within(1e-6));
        assertThat(found.get(1).distance()).isCloseTo(0.2, within(1e-6));
        assertThat(found.getFirst().documentTitle()).isEqualTo("INDEXED");
        assertThat(vectorSearch.nearestChunks(query, 1, 0.4)).hasSize(1);
        assertThat(vectorSearch.nearestChunks(query, 10, 0.1)).hasSize(1);
    }

    private KnowledgeDocument document(Admin admin, DocumentStatus status) {
        return KnowledgeDocument.builder().admin(admin).title(status.name()).fileUrl("it.txt").fileType("TXT").status(status).build();
    }

    private static void chunk(KnowledgeDocument document, int index, String content, float[] embedding) {
        document.getChunks().add(DocumentChunk.builder().knowledgeDocument(document).chunkIndex(index)
                .content(content).embedding(embedding).build());
    }

    // vector 768 chiều chỉ có 2 thành phần đầu khác 0
    static float[] unit(double x, double y) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = (float) x;
        vector[1] = (float) y;
        return vector;
    }
}
