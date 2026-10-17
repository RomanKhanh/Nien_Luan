package com.brainblocks.backend.support;

import com.brainblocks.backend.entity.DocumentChunk;
import com.brainblocks.backend.entity.ProductEmbedding;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.repository.VectorSearchRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Bản thay thế cho PgVectorSearchRepository khi test chạy H2 (không có toán tử {@code <=>} của pgvector):
 * tính khoảng cách cosine bằng Java trên đúng dữ liệu trong H2, cùng điều kiện lọc và thứ tự như câu SQL thật.
 * Câu SQL thật được kiểm tra ở PgVectorSearchIntegrationTest (profile pgvector-it). Là @Component trong src/test nên mọi
 * @SpringBootTest tự quét thấy (@Primary thắng bản PostgreSQL); test lát cắt JPA thì @Import vào.
 */
@Primary
@Component
public class InMemoryVectorSearchRepository implements VectorSearchRepository {
    private final EntityManager em;

    public InMemoryVectorSearchRepository(EntityManager em) { this.em = em; }

    @Override
    @Transactional(readOnly = true)
    public List<ChunkMatch> nearestChunks(float[] query, int limit, double maxDistance) {
        return em.createQuery("select c from DocumentChunk c join fetch c.knowledgeDocument d "
                        + "where d.status = :status and c.embedding is not null", DocumentChunk.class)
                .setParameter("status", DocumentStatus.INDEXED).getResultList().stream()
                .map(c -> new ChunkMatch(c.getId(), c.getKnowledgeDocument().getId(), c.getKnowledgeDocument().getTitle(),
                        c.getContent(), distance(query, c.getEmbedding())))
                .filter(m -> Double.isFinite(m.distance()))
                .sorted(Comparator.comparingDouble(ChunkMatch::distance).thenComparing(ChunkMatch::chunkId))
                .limit(limit)
                .filter(m -> m.distance() <= maxDistance)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductMatch> nearestProducts(float[] query, Integer age, Collection<Long> excludedIds, int limit, double maxDistance) {
        return em.createQuery("select e from ProductEmbedding e join fetch e.product p "
                        + "where p.active = true and p.stockQuantity > 0", ProductEmbedding.class).getResultList().stream()
                .filter(e -> age == null || (e.getProduct().getMinAge() <= age && e.getProduct().getMaxAge() >= age))
                .filter(e -> excludedIds == null || !excludedIds.contains(e.getProduct().getId()))
                .map(e -> new ProductMatch(e.getProduct().getId(), distance(query, e.getEmbedding())))
                .filter(m -> Double.isFinite(m.distance()))
                .sorted(Comparator.comparingDouble(ProductMatch::distance).thenComparing(ProductMatch::productId))
                .limit(limit)
                .filter(m -> m.distance() <= maxDistance)
                .toList();
    }

    // Khoảng cách cosine như pgvector: 1 - cosine; vector lệch chiều hoặc bằng 0 thì NaN (bị loại).
    public static double distance(float[] a, float[] b) {
        Objects.requireNonNull(a);
        if (b == null || a.length == 0 || a.length != b.length) return Double.NaN;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            if (!Float.isFinite(a[i]) || !Float.isFinite(b[i])) return Double.NaN;
            dot += a[i] * (double) b[i]; aa += a[i] * (double) a[i]; bb += b[i] * (double) b[i];
        }
        return aa == 0 || bb == 0 ? Double.NaN : 1 - dot / Math.sqrt(aa * bb);
    }
}
