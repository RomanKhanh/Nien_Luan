package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductEmbedding;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.ProductEmbeddingRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Giữ vector ngữ nghĩa của sản phẩm (bảng product_embeddings) khớp với dữ liệu sản phẩm.
 * Chỉ gọi Gemini cho sản phẩm có văn bản embed đổi (so content_hash); sản phẩm ngừng bán không embed
 * (chatbot cũng lọc theo active). Mọi lời gọi mạng nằm ngoài transaction, như KnowledgeIndexingWorker.
 * Tác vụ nền chạy tuần tự trên productEmbeddingExecutor (1 luồng) nên không ghi đè lẫn nhau.
 */
@Service
@Slf4j
public class ProductEmbeddingService {
    static final int BATCH = 50;

    private final ProductRepository products;
    private final SkillRepository skills;
    private final ProductEmbeddingRepository embeddings;
    private final GeminiService gemini;
    private final TransactionTemplate tx;

    public ProductEmbeddingService(ProductRepository products, SkillRepository skills, ProductEmbeddingRepository embeddings,
            GeminiService gemini, PlatformTransactionManager manager) {
        this.products = products;
        this.skills = skills;
        this.embeddings = embeddings;
        this.gemini = gemini;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sản phẩm / danh mục / kỹ năng vừa đổi (phát từ AdminProductService, CategoryService, SkillService).
     * Chạy sau khi transaction của admin commit; lỗi chỉ ghi log, thao tác lưu của admin không bị ảnh hưởng.
     */
    @Async("productEmbeddingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductContentChanged(ProductContentChangedEvent event) {
        try {
            if (event.productIds().isEmpty()) refreshAllStale();
            else refresh(event.productIds());
        } catch (RuntimeException ex) {
            log.warn("Product embedding refresh failed: {} ({})", ex.getMessage(), ex.getClass().getSimpleName());
        }
    }

    // Lúc khởi động (ProductEmbeddingBackfillRunner): embed sản phẩm cũ chưa có vector hoặc vector đã lỗi thời.
    @Async("productEmbeddingExecutor")
    public void backfillAsync() {
        try {
            refreshAllStale();
        } catch (RuntimeException ex) {
            // thường là hết quota / mất mạng: lần khởi động sau làm tiếp phần còn thiếu (dựa vào hash)
            log.warn("Product embedding backfill stopped: {} ({})", ex.getMessage(), ex.getClass().getSimpleName());
        }
    }

    // Duyệt mọi sản phẩm đang bán theo lô BATCH; mỗi lô tối đa 1 request embed. Trả số sản phẩm đã embed.
    public int refreshAllStale() {
        long total = products.countByActiveTrue();
        long checked = 0;
        int refreshed = 0;
        long afterId = 0;
        List<Long> ids;
        while (!(ids = products.findActiveIdsAfter(afterId, PageRequest.of(0, BATCH))).isEmpty()) {
            refreshed += refresh(ids);
            checked += ids.size();
            afterId = ids.getLast();
            log.info("Product embeddings: {}/{} checked, {} refreshed", checked, total, refreshed);
        }
        return refreshed;
    }

    // Embed lại các sản phẩm trong ids có văn bản khác lần embed trước. Trả số sản phẩm đã embed.
    public int refresh(Collection<Long> ids) {
        int refreshed = 0;
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        for (int from = 0; from < distinct.size(); from += BATCH) {
            refreshed += refreshBatch(distinct.subList(from, Math.min(from + BATCH, distinct.size())));
        }
        return refreshed;
    }

    private int refreshBatch(List<Long> ids) {
        List<Pending> pending = tx.execute(status -> stale(ids));
        if (pending == null || pending.isEmpty()) return 0;
        List<float[]> vectors = gemini.embedDocuments(pending.stream().map(Pending::text).toList());
        Integer saved = tx.execute(status -> save(pending, vectors));
        return saved == null ? 0 : saved;
    }

    // Sản phẩm đang bán có hash văn bản khác hash đã lưu (hoặc chưa có vector).
    private List<Pending> stale(Collection<Long> ids) {
        Map<Long, String> stored = embeddings.findHashes(ids).stream().collect(Collectors.toMap(
                ProductEmbeddingRepository.StoredHash::getProductId, ProductEmbeddingRepository.StoredHash::getContentHash));
        List<Skill> allSkills = skills.findAllByOrderByIdAsc();
        List<Pending> result = new ArrayList<>();
        for (Product product : products.findForEmbedding(ids)) {
            if (!product.isActive()) continue;
            String text = ProductEmbeddingText.build(product, allSkills);
            String hash = ProductEmbeddingText.hash(text);
            if (!hash.equals(stored.get(product.getId()))) result.add(new Pending(product.getId(), text, hash));
        }
        result.sort(Comparator.comparing(Pending::productId));
        return result;
    }

    // Admin có thể sửa sản phẩm trong lúc chờ Gemini: chỉ lưu vector nếu văn bản hiện tại vẫn đúng văn bản đã embed
    // (lần sửa đó đã phát event riêng và được xử lý ở lượt sau).
    private int save(List<Pending> pending, List<float[]> vectors) {
        Map<Long, Pending> current = stale(pending.stream().map(Pending::productId).toList()).stream()
                .collect(Collectors.toMap(Pending::productId, Function.identity()));
        Map<Long, ProductEmbedding> existing = embeddings.findAllByProductIdIn(current.keySet()).stream()
                .collect(Collectors.toMap(e -> e.getProduct().getId(), Function.identity()));
        int saved = 0;
        for (int i = 0; i < pending.size(); i++) {
            Pending item = pending.get(i);
            Pending now = current.get(item.productId());
            if (now == null || !now.hash().equals(item.hash())) continue;
            ProductEmbedding embedding = existing.get(item.productId());
            if (embedding == null) {
                embedding = ProductEmbedding.builder().product(products.getReferenceById(item.productId())).build();
            }
            embedding.setEmbedding(vectors.get(i));
            embedding.setContentHash(item.hash());
            embeddings.save(embedding);
            saved++;
        }
        return saved;
    }

    private record Pending(Long productId, String text, String hash) {}

    /** Văn bản embed của các sản phẩm có thể đã đổi; danh sách rỗng = kiểm tra mọi sản phẩm (đổi tên danh mục / kỹ năng). */
    public record ProductContentChangedEvent(List<Long> productIds) {
        public static ProductContentChangedEvent of(Long productId) { return new ProductContentChangedEvent(List.of(productId)); }
        public static ProductContentChangedEvent all() { return new ProductContentChangedEvent(List.of()); }
    }
}
