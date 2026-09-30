package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.request.product.ProductRequest;
import com.brainblocks.backend.dto.request.product.ProductStatusRequest;
import com.brainblocks.backend.dto.request.skill.SkillImpactRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductImageResponse;
import com.brainblocks.backend.dto.response.product.ProductResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.dto.response.review.ReviewSummaryResponse;
import com.brainblocks.backend.dto.response.skill.SkillImpactResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ReviewRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;
    private final ReviewRepository reviewRepository;
    private final SkillProfileService skillProfileService;

    // ===== Phía khách: chỉ thấy sản phẩm active =====

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(Long categoryId, String keyword, BigDecimal minPrice,
                                                       BigDecimal maxPrice, Integer age, int page, int size) {
        // sort cố định phía server (mới nhất trước), không nhận field sort từ client
        Pageable pageable = buildPageable(page, size, Sort.Direction.DESC, "createdAt");
        Page<Product> result = productRepository.searchActiveProducts(
                categoryId, normalizeKeyword(keyword), minPrice, maxPrice, age, pageable);

        Map<Long, String> thumbnails = loadThumbnails(result.getContent());
        return PageResponse.of(result, product -> toSummaryResponse(product, thumbnails.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveById(Long id) {
        Product product = findProduct(id);
        if (!product.isActive()) {
            // không tiết lộ sản phẩm đã ẩn qua việc đoán id, coi như không tồn tại với khách
            throw new ResourceNotFoundException("Product not found");
        }
        return toResponse(product);
    }

    // ===== Phía admin =====

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchForAdmin(Long categoryId, String keyword, int page, int size) {
        Pageable pageable = buildPageable(page, size, Sort.Direction.DESC, "createdAt");
        Page<Product> result = productRepository.searchAllForAdmin(normalizeKeyword(keyword), categoryId, pageable);

        Map<Long, String> thumbnails = loadThumbnails(result.getContent());
        return PageResponse.of(result, product -> toSummaryResponse(product, thumbnails.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByIdForAdmin(Long id) {
        return toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = findCategory(request.categoryId());

        Product product = Product.builder()
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .minAge(request.minAge())
                .maxAge(request.maxAge())
                .category(category)
                .build();
        applyImpacts(product, request.skillImpacts());

        // sản phẩm mới chưa bé nào sở hữu nên không cần tính lại SkillProfile
        return toResponse(productRepository.save(product));
    }

    // PUT thay toàn bộ, kể cả skillImpacts; sau khi đổi impactIndex phải tính lại SkillProfile
    // của mọi bé đang sở hữu sản phẩm này (comment sẵn trong SkillProfileService.recalculateForProduct)
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        Category category = findCategory(request.categoryId());

        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setMinAge(request.minAge());
        product.setMaxAge(request.maxAge());
        product.setCategory(category);
        applyImpacts(product, request.skillImpacts());

        ProductResponse response = toResponse(product);
        skillProfileService.recalculateForProduct(product.getId());
        return response;
    }

    // ẩn/hiện thay vì xóa cứng vì sản phẩm còn gắn với đơn hàng cũ (Product.isActive trên sơ đồ lớp)
    @Transactional
    public ProductResponse updateStatus(Long id, ProductStatusRequest request) {
        Product product = findProduct(id);
        product.setActive(request.active());
        return toResponse(product);
    }

    // ===== Mapping =====

    private Pageable buildPageable(int page, int size, Sort.Direction direction, String property) {
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(direction, property));
    }

    // thay toàn bộ danh sách impact; kiểm tra trùng skillId trước để không văng lỗi unique constraint từ DB
    private void applyImpacts(Product product, List<SkillImpactRequest> impacts) {
        product.getProductSkillImpacts().clear();
        // đẩy DELETE của các impact cũ xuống DB ngay: Hibernate mặc định chạy hết INSERT rồi mới tới
        // DELETE trong 1 lần flush, nên nếu impact mới trùng skillId với impact cũ (rất thường xảy ra
        // khi PUT không đổi skill nào, chỉ đổi impactIndex) sẽ đụng unique constraint {product_id, skill_id}
        // trước khi dòng cũ kịp bị xóa. Kỹ thuật này giống hệt SkillProfileService.recalculateFor().
        productRepository.flush();

        Set<Long> seenSkillIds = new HashSet<>();

        for (SkillImpactRequest item : impacts) {
            if (!seenSkillIds.add(item.skillId())) {
                throw new IllegalArgumentException("Duplicate skillId in skillImpacts: " + item.skillId());
            }
            Skill skill = skillRepository.findById(item.skillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Skill not found: " + item.skillId()));

            product.getProductSkillImpacts().add(ProductSkillImpact.builder()
                    .product(product)
                    .skill(skill)
                    .impactIndex(item.impactIndex())
                    .build());
        }
    }

    // ảnh đại diện của cả trang trong 1 câu, tránh N+1 khi liệt kê nhiều sản phẩm
    private Map<Long, String> loadThumbnails(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> productIds = products.stream().map(Product::getId).toList();
        return productRepository.findThumbnails(productIds).stream()
                .collect(Collectors.toMap(ProductRepository.ProductThumbnail::getProductId, ProductRepository.ProductThumbnail::getUrl, (a, b) -> a));
    }

    private ProductResponse toResponse(Product product) {
        List<ProductImageResponse> images = product.getProductImages().stream()
                .map(this::toImageResponse)
                .toList();
        List<SkillImpactResponse> skillImpacts = product.getProductSkillImpacts().stream()
                .map(this::toSkillImpactResponse)
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getMinAge(),
                product.getMaxAge(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                images,
                skillImpacts,
                toReviewSummary(product.getId()),
                product.getCreatedAt()
        );
    }

    private ProductSummaryResponse toSummaryResponse(Product product, String thumbnailUrl) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                thumbnailUrl,
                product.getMinAge(),
                product.getMaxAge(),
                product.getStockQuantity() > 0
        );
    }

    private ProductImageResponse toImageResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getUrl(), image.getDisplayOrder(), image.isThumbnail());
    }

    private SkillImpactResponse toSkillImpactResponse(ProductSkillImpact impact) {
        Skill skill = impact.getSkill();
        return new SkillImpactResponse(skill.getId(), skill.getCode(), skill.getName(), impact.getImpactIndex());
    }

    private ReviewSummaryResponse toReviewSummary(Long productId) {
        ReviewRepository.ReviewSummary summary = reviewRepository.getSummaryByProductId(productId);
        double avgRating = summary.getAvgRating() == null ? 0 : round1(summary.getAvgRating());
        long totalReviews = summary.getTotalReviews() == null ? 0 : summary.getTotalReviews();
        return new ReviewSummaryResponse(avgRating, totalReviews);
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
