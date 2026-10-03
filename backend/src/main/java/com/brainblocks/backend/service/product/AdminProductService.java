package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.request.product.ProductRequest;
import com.brainblocks.backend.dto.request.product.SkillImpactRequest;
import com.brainblocks.backend.dto.request.product.UpdateStockRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductDetailResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quản lý sản phẩm cho admin (đề 2.8): thêm / sửa / ẩn, tồn kho, độ tuổi, danh mục, chỉ số tác động kỹ năng.
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final ProductService productService;
    private final SkillProfileService skillProfileService;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, ProductSort sort,
                                                       int page, int size) {
        return productService.search(criteria, sort, page, size);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(Long id) {
        return productService.toDetailResponse(findProduct(id));
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .active(request.active() == null || request.active())
                .build();
        applyRequest(product, request);
        return productService.toDetailResponse(productRepository.save(product));
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        if (request.active() != null) {
            product.setActive(request.active());
        }
        boolean impactsChanged = applyRequest(product, request);
        if (impactsChanged) {
            // hồ sơ kỹ năng của các bé đang có sản phẩm này phải phản ánh chỉ số mới (đề 2.9)
            skillProfileService.recalculateForProduct(product.getId());
        }
        // flush để chỉ số vừa thêm có id trước khi dựng response
        productRepository.flush();
        return productService.toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse updateStock(Long id, UpdateStockRequest request) {
        Product product = findProduct(id);
        product.setStockQuantity(request.stockQuantity());
        return productService.toDetailResponse(product);
    }

    /**
     * "Xóa" sản phẩm = ẩn khỏi cửa hàng (active = false), không xóa cứng vì sản phẩm còn gắn với đơn hàng,
     * đánh giá và hồ sơ trẻ. Admin bật lại bằng cách sửa sản phẩm với active = true.
     */
    @Transactional
    public void deleteProduct(Long id) {
        findProduct(id).setActive(false);
    }

    private Product findProduct(Long id) {
        return productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    // trả true nếu chỉ số tác động kỹ năng thay đổi
    private boolean applyRequest(Product product, ProductRequest request) {
        if (request.minAge() > request.maxAge()) {
            throw new IllegalArgumentException("minAge must not be greater than maxAge");
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        product.setName(request.name().trim());
        product.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        product.setVideoUrl(request.videoUrl() == null || request.videoUrl().isBlank()
                ? null : request.videoUrl().trim());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setMinAge(request.minAge());
        product.setMaxAge(request.maxAge());
        product.setWeightGrams(request.weightGrams());
        product.setLengthCm(request.lengthCm());
        product.setWidthCm(request.widthCm());
        product.setHeightCm(request.heightCm());
        product.setCategory(category);
        return applyImpacts(product, request.skillImpacts() == null ? List.of() : request.skillImpacts());
    }

    /**
     * Cập nhật tại chỗ thay vì xóa hết rồi thêm lại: Hibernate flush INSERT trước DELETE,
     * nên thêm lại cùng (product, skill) sẽ vướng uk_product_skill_impacts_product_skill.
     * impactIndex = 0 nghĩa là không tác động, không lưu dòng.
     */
    private boolean applyImpacts(Product product, List<SkillImpactRequest> requests) {
        Map<Long, Integer> wanted = new HashMap<>();
        for (SkillImpactRequest r : requests) {
            if (wanted.put(r.skillId(), r.impactIndex()) != null) {
                throw new IllegalArgumentException("Skill " + r.skillId() + " is listed more than once");
            }
        }
        wanted.values().removeIf(value -> value == 0);
        Map<Long, Skill> skills = skillRepository.findAllById(wanted.keySet()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        if (skills.size() != wanted.size()) {
            throw new ResourceNotFoundException("Skill not found");
        }
        boolean changed = product.getProductSkillImpacts()
                .removeIf(impact -> !wanted.containsKey(impact.getSkill().getId()));
        Map<Long, ProductSkillImpact> existing = product.getProductSkillImpacts().stream()
                .collect(Collectors.toMap(impact -> impact.getSkill().getId(), Function.identity()));
        for (Map.Entry<Long, Integer> entry : wanted.entrySet()) {
            ProductSkillImpact impact = existing.get(entry.getKey());
            if (impact == null) {
                product.getProductSkillImpacts().add(ProductSkillImpact.builder()
                        .product(product)
                        .skill(skills.get(entry.getKey()))
                        .impactIndex(entry.getValue())
                        .build());
                changed = true;
            } else if (impact.getImpactIndex() != entry.getValue()) {
                impact.setImpactIndex(entry.getValue());
                changed = true;
            }
        }
        return changed;
    }
}
