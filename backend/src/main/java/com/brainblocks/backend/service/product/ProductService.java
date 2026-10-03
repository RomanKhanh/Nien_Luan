package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductDetailResponse;
import com.brainblocks.backend.dto.response.product.ProductImageResponse;
import com.brainblocks.backend.dto.response.product.ProductSkillImpactResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductImage;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.OrderItemRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductRepository.ProductThumbnail;
import com.brainblocks.backend.repository.ProductSkillImpactRepository;
import com.brainblocks.backend.repository.ReviewRepository;
import com.brainblocks.backend.repository.ReviewRepository.RatingSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Danh sách / chi tiết sản phẩm cho phía khách hàng (đề 2.2); AdminProductService dùng lại phần mapping.
 */
@Service
@RequiredArgsConstructor
public class ProductService {
    // lọc theo nhóm kỹ năng: sản phẩm phải tác động ít nhất mức này (thang 0 - 10) vào nhóm được chọn,
    // để "lọc theo Sáng tạo" không trả về cả những món chỉ dính 1 - 2 điểm Sáng tạo
    public static final int SKILL_FILTER_MIN_IMPACT = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductSkillImpactRepository productSkillImpactRepository;
    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, ProductSort sort,
                                                       int page, int size) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("minPrice must not be greater than maxPrice");
        }
        boolean byRelevance = sort == ProductSort.RELEVANCE;
        // RELEVANCE tự gắn ORDER BY trong Specification (sắp theo subquery), nên không truyền Sort
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                byRelevance ? Sort.unsorted() : toSort(sort));
        Page<Product> products = productRepository.findAll(
                ProductSpecifications.matching(criteria, SKILL_FILTER_MIN_IMPACT, byRelevance), pageable);
        return toSummaryPage(products);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getActiveProduct(Long id) {
        Product product = productRepository.findWithCategoryById(id)
                .filter(Product::isActive) // sản phẩm đã ẩn coi như không tồn tại với phía khách hàng
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toDetailResponse(product);
    }

    // ===== mapping, dùng chung với AdminProductService =====

    PageResponse<ProductSummaryResponse> toSummaryPage(Page<Product> products) {
        List<Long> ids = products.getContent().stream().map(Product::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.of(products, product -> null);
        }
        // chỉ số kỹ năng, ảnh đại diện, điểm đánh giá của cả trang: mỗi loại 1 câu
        Map<Long, List<ProductSkillImpactResponse>> impactsByProduct = productSkillImpactRepository
                .findAllWithSkillByProductIdIn(ids).stream()
                .collect(Collectors.groupingBy(psi -> psi.getProduct().getId(),
                        Collectors.mapping(this::toImpactResponse, Collectors.toList())));
        Map<Long, String> thumbnailByProduct = productRepository.findThumbnails(ids).stream()
                .collect(Collectors.toMap(ProductThumbnail::getProductId, ProductThumbnail::getUrl, (a, b) -> a));
        Map<Long, RatingSummary> ratingByProduct = reviewRepository.summarizeByProductIds(ids).stream()
                .collect(Collectors.toMap(RatingSummary::getProductId, Function.identity()));
        return PageResponse.of(products, product -> {
            RatingSummary rating = ratingByProduct.get(product.getId());
            return new ProductSummaryResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getMinAge(),
                    product.getMaxAge(),
                    product.getStockQuantity(),
                    product.isActive(),
                    thumbnailByProduct.get(product.getId()),
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    impactsByProduct.getOrDefault(product.getId(), List.of()),
                    rating == null ? 0 : roundRating(rating.getAverage()),
                    rating == null ? 0 : rating.getTotal());
        });
    }

    ProductDetailResponse toDetailResponse(Product product) {
        List<ProductSkillImpactResponse> impacts = productSkillImpactRepository
                .findAllWithSkillByProductIdIn(List.of(product.getId())).stream()
                .map(this::toImpactResponse)
                .toList();
        List<ProductImageResponse> images = product.getProductImages().stream()
                .sorted(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(image -> new ProductImageResponse(image.getId(), image.getUrl(),
                        image.getDisplayOrder(), image.isThumbnail()))
                .toList();
        Map<Integer, Long> breakdown = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            breakdown.put(star, 0L);
        }
        reviewRepository.countByRating(product.getId())
                .forEach(row -> breakdown.put(row.getRating(), row.getTotal()));
        long reviewCount = breakdown.values().stream().mapToLong(Long::longValue).sum();
        double average = reviewCount == 0 ? 0 : roundRating(breakdown.entrySet().stream()
                .mapToDouble(e -> e.getKey() * (double) e.getValue()).sum() / reviewCount);
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getVideoUrl(),
                product.getPrice(),
                product.getMinAge(),
                product.getMaxAge(),
                product.getStockQuantity(),
                product.getWeightGrams(),
                product.getLengthCm(),
                product.getWidthCm(),
                product.getHeightCm(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                images,
                impacts,
                average,
                reviewCount,
                breakdown,
                orderItemRepository.sumSoldQuantity(product.getId(), OrderStatus.CANCELLED),
                product.getCreatedAt());
    }

    private ProductSkillImpactResponse toImpactResponse(ProductSkillImpact impact) {
        return new ProductSkillImpactResponse(impact.getSkill().getId(), impact.getSkill().getCode(),
                impact.getSkill().getName(), impact.getImpactIndex());
    }

    private Sort toSort(ProductSort sort) {
        // thêm id làm tiêu chí phụ để phân trang ổn định khi nhiều sản phẩm trùng giá / trùng ngày
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"));
            case NAME -> Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
            case NEWEST, RELEVANCE -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    // làm tròn 1 chữ số thập phân (4,6 sao)
    private double roundRating(Double average) {
        return average == null ? 0 : Math.round(average * 10) / 10.0;
    }
}
