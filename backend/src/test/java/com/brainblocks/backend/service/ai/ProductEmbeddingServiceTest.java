package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.dto.request.product.ProductRequest;
import com.brainblocks.backend.dto.request.product.SkillImpactRequest;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.exception.AiServiceException;
import com.brainblocks.backend.repository.ProductEmbeddingRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.product.AdminProductService;
import com.brainblocks.backend.service.product.ProductService;
import com.brainblocks.backend.service.skill.SkillProfileService;
import com.brainblocks.backend.service.skill.SkillScoreCalculator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

// H2 + Gemini giả lập: kiểm tra văn bản embed, content_hash (không gọi lại AI khi không đổi), backfill theo lô
// và lỗi embed không làm hỏng việc lưu sản phẩm. Lát cắt JPA không bật @Async nên tác vụ nền chạy đồng bộ.
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ProductEmbeddingService.class, AdminProductService.class, ProductService.class, SkillProfileService.class,
        SkillScoreCalculator.class, ChildProfileAccessGuard.class, CurrentUserProvider.class})
class ProductEmbeddingServiceTest {
    @MockitoBean GeminiService gemini;
    @Autowired ProductEmbeddingService service;
    @Autowired AdminProductService adminProducts;
    @Autowired ProductEmbeddingRepository embeddings;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    TransactionTemplate tx;
    Category category;
    Skill logic;
    Skill creative;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from ProductEmbedding").executeUpdate();
            em.createQuery("delete from ProductSkillImpact").executeUpdate();
            em.createQuery("delete from Product").executeUpdate();
            em.createQuery("delete from Skill").executeUpdate();
            category = Category.builder().name("Lắp ráp " + UUID.randomUUID()).build();
            logic = Skill.builder().code("LOGIC").name("Tư duy Logic").build();
            creative = Skill.builder().code("CREATIVE").name("Sáng tạo").build();
            em.persist(category); em.persist(logic); em.persist(creative);
        });
        when(gemini.embedDocuments(anyList())).thenAnswer(invocation -> vectors(invocation.getArgument(0)));
    }

    @Test
    void embeddingTextDescribesProductAgeAndSkillImpactsAsSentences() {
        Product product = Product.builder().name(" Bộ xếp hình ").description("Rèn sự kiên nhẫn.").minAge(4).maxAge(8)
                .category(Category.builder().name("Xếp hình").build()).build();
        Skill problem = Skill.builder().id(3L).code("PROBLEM_SOLVING").name("Giải quyết vấn đề").build();
        Skill stem = Skill.builder().id(4L).code("STEM").name("STEM").build();
        Skill l = Skill.builder().id(1L).code("LOGIC").name("Tư duy Logic").build();
        Skill c = Skill.builder().id(2L).code("CREATIVE").name("Sáng tạo").build();
        product.getProductSkillImpacts().add(ProductSkillImpact.builder().skill(c).impactIndex(3).build());
        product.getProductSkillImpacts().add(ProductSkillImpact.builder().skill(l).impactIndex(8).build());
        product.getProductSkillImpacts().add(ProductSkillImpact.builder().skill(problem).impactIndex(5).build());

        String text = ProductEmbeddingText.build(product, List.of(l, c, problem, stem));

        assertThat(text).isEqualTo("""
                Sản phẩm: Bộ xếp hình.
                Danh mục: Xếp hình.
                Độ tuổi phù hợp: từ 4 đến 8 tuổi.
                Mô tả: Rèn sự kiên nhẫn.
                Tác động đến kỹ năng của trẻ (thang 0-10):
                - Tư duy Logic: 8/10, tác động mạnh.
                - Giải quyết vấn đề: 5/10, tác động vừa.
                - Sáng tạo: 3/10, tác động nhẹ.
                Ít tác động đến: STEM.""");
        String hash = ProductEmbeddingText.hash(text);
        assertThat(hash).hasSize(64).isEqualTo(ProductEmbeddingText.hash(text));
        assertThat(ProductEmbeddingText.hash(text + " ")).isNotEqualTo(hash);
        // mô tả quá dài bị cắt để văn bản embed có độ dài giới hạn
        product.setDescription("a".repeat(5000));
        assertThat(ProductEmbeddingText.build(product, List.of())).contains("a".repeat(ProductEmbeddingText.MAX_DESCRIPTION) + "\n")
                .doesNotContain("a".repeat(ProductEmbeddingText.MAX_DESCRIPTION + 1));
    }

    @Test
    void unchangedProductIsNotEmbeddedAgainAndChangedOneIs() {
        Long id = product("Robot lập trình", true, 8);

        assertThat(service.refresh(List.of(id))).isEqualTo(1);
        assertThat(service.refresh(List.of(id))).isZero();
        verify(gemini, times(1)).embedDocuments(anyList());
        String firstHash = storedHash(id);

        // đổi chỉ số kỹ năng làm văn bản embed đổi: gọi Gemini lại đúng 1 lần, vẫn 1 dòng cho sản phẩm
        tx.executeWithoutResult(status -> em.createQuery("select i from ProductSkillImpact i where i.product.id = :id",
                ProductSkillImpact.class).setParameter("id", id).getSingleResult().setImpactIndex(2));
        assertThat(service.refresh(List.of(id))).isEqualTo(1);
        verify(gemini, times(2)).embedDocuments(anyList());
        assertThat(storedHash(id)).isNotEqualTo(firstHash);
        assertThat(embeddings.count()).isEqualTo(1);

        // đổi tồn kho không nằm trong văn bản embed: không gọi Gemini
        tx.executeWithoutResult(status -> em.find(Product.class, id).setStockQuantity(1));
        assertThat(service.refresh(List.of(id))).isZero();
        verify(gemini, times(2)).embedDocuments(anyList());
    }

    @Test
    void inactiveProductsAreNotEmbedded() {
        Long hidden = product("Hàng ngừng bán", false, 5);
        assertThat(service.refresh(List.of(hidden))).isZero();
        verifyNoInteractions(gemini);
    }

    @Test
    void backfillEmbedsOnlyMissingProductsInBatchesOfFifty() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 120; i++) ids.add(product("Sản phẩm " + i, true, 5));
        product("Ngừng bán", false, 5);
        service.refresh(ids.subList(0, 10));
        clearInvocations(gemini);

        assertThat(service.refreshAllStale()).isEqualTo(110);
        // 120 sản phẩm đang bán chia 3 lô (50, 50, 20); 10 món đã có vector đúng không gửi lại
        verify(gemini, times(3)).embedDocuments(anyList());
        assertThat(embeddings.count()).isEqualTo(120);
        clearInvocations(gemini);
        assertThat(service.refreshAllStale()).isZero();
        verifyNoInteractions(gemini);
    }

    @Test
    void embeddingFailureDoesNotBreakSavingTheProduct() {
        when(gemini.embedDocuments(anyList())).thenThrow(new AiServiceException("quota"));
        var created = adminProducts.createProduct(request("Đồ chơi mới"));
        assertThat(em.find(Product.class, created.id())).isNotNull();
        assertThat(embeddings.count()).isZero();

        // Gemini hoạt động lại: lượt sửa sau (hoặc backfill) tạo vector
        doAnswer(invocation -> vectors(invocation.getArgument(0))).when(gemini).embedDocuments(anyList());
        adminProducts.updateProduct(created.id(), request("Đồ chơi mới đã sửa"));
        assertThat(embeddings.count()).isEqualTo(1);
    }

    @Test
    void deletingProductRowRemovesItsEmbedding() {
        Long id = product("Sẽ bị xóa", true, 5);
        service.refresh(List.of(id));
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from ProductSkillImpact i where i.product.id = :id").setParameter("id", id).executeUpdate();
            em.createNativeQuery("delete from products where id = " + id).executeUpdate();
        });
        assertThat(embeddings.count()).isZero();
    }

    private Long product(String name, boolean active, int logicImpact) {
        return tx.execute(status -> {
            Product product = Product.builder().name(name).description("Mô tả " + name).price(BigDecimal.TEN)
                    .stockQuantity(10).minAge(4).maxAge(10).category(em.merge(category)).active(active)
                    .weightGrams(500).lengthCm(20).widthCm(15).heightCm(10).build();
            product.getProductSkillImpacts().add(ProductSkillImpact.builder().product(product)
                    .skill(em.merge(logic)).impactIndex(logicImpact).build());
            em.persist(product);
            return product.getId();
        });
    }

    private ProductRequest request(String name) {
        return new ProductRequest(name, "Mô tả", null, BigDecimal.TEN, 5, 3, 9, category.getId(), 500, 20, 15, 10,
                List.of(new SkillImpactRequest(creative.getId(), 6)), null);
    }

    private String storedHash(Long productId) {
        return embeddings.findHashes(List.of(productId)).getFirst().getContentHash();
    }

    private static List<float[]> vectors(List<String> texts) {
        return texts.stream().map(t -> new float[]{1, 0}).toList();
    }
}
