package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductMatchResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.dto.response.skill.SkillGainResponse;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductSkillImpactRepository;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Danh sách sản phẩm "Hợp với bé": lọc như trang sản phẩm nhưng luôn theo đúng tuổi bé, bỏ món bé đã có,
 * và kèm mức tăng điểm kỹ năng dự kiến của bé cho từng sản phẩm.
 *
 * sort = fit: sắp theo tổng mức tăng ("bổ sung nhiều nhất cho bé"). Mức tăng tính theo công thức lợi ích giảm dần
 * nên không sắp được bằng SQL: lấy hết sản phẩm khớp bộ lọc rồi sắp trong bộ nhớ. Đủ nhanh với quy mô catalog
 * của cửa hàng; catalog lên nhiều nghìn sản phẩm thì cần lưu sẵn điểm hoặc giới hạn ứng viên.
 * Các kiểu sắp xếp khác đi qua ProductService.search như bình thường.
 */
@Service
@RequiredArgsConstructor
public class ProductMatchService {
    public static final String SORT_FIT = "fit";
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final ProductSkillImpactRepository productSkillImpactRepository;
    private final ProductService productService;
    private final SkillProfileService skillProfileService;
    private final ChildProfileAccessGuard accessGuard;

    /**
     * @param filters bộ lọc phụ huynh chọn; độ tuổi trong đó bị bỏ qua và thay bằng tuổi của bé
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductMatchResponse> match(Long childId, ProductSearchCriteria filters, String sort,
                                                    int page, int size) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        int age = child.getAge();
        ProductSearchCriteria criteria = new ProductSearchCriteria(filters.keyword(), filters.categoryId(),
                age, age, filters.skillCodes(), filters.minPrice(), filters.maxPrice(), filters.inStockOnly(),
                false, child.getId());
        Map<Long, List<Integer>> current = skillProfileService.currentImpacts(child.getId());
        Set<String> focusSkills = criteria.hasSkillFilter() ? Set.copyOf(criteria.skillCodes()) : Set.of();

        if (sort == null || sort.isBlank() || SORT_FIT.equalsIgnoreCase(sort.trim())) {
            return matchByFit(criteria, current, focusSkills, page, Math.min(size, MAX_PAGE_SIZE));
        }
        PageResponse<ProductSummaryResponse> summaries =
                productService.search(criteria, ProductSort.from(sort), page, size);
        Map<Long, List<ProductSkillImpact>> impacts =
                impactsByProduct(summaries.content().stream().map(ProductSummaryResponse::id).toList());
        return withGains(summaries, impacts, current, focusSkills);
    }

    private PageResponse<ProductMatchResponse> matchByFit(ProductSearchCriteria criteria,
                                                          Map<Long, List<Integer>> current,
                                                          Set<String> focusSkills, int page, int size) {
        // page âm / size 0 bị PageRequest từ chối (IllegalArgumentException -> 400) trước khi cắt danh sách
        PageRequest pageable = PageRequest.of(page, size);
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("minPrice must not be greater than maxPrice");
        }
        List<Product> candidates = productRepository.findAll(
                ProductSpecifications.matching(criteria, ProductService.SKILL_FILTER_MIN_IMPACT, false));
        Map<Long, List<ProductSkillImpact>> impacts =
                impactsByProduct(candidates.stream().map(Product::getId).toList());
        Map<Long, Double> fitByProduct = candidates.stream().collect(Collectors.toMap(Product::getId,
                product -> fitScore(skillProfileService.projectGains(current,
                        impacts.getOrDefault(product.getId(), List.of())), focusSkills)));

        // bổ sung nhiều nhất trước; bằng nhau thì giá thấp hơn, rồi id để phân trang ổn định
        List<Product> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble((Product p) -> fitByProduct.get(p.getId())).reversed()
                        .thenComparing(Product::getPrice)
                        .thenComparing(Product::getId))
                .toList();
        int from = Math.min(page * size, ranked.size());
        List<Product> slice = ranked.subList(from, Math.min(from + size, ranked.size()));
        PageResponse<ProductSummaryResponse> summaries = productService.toSummaryPage(
                new PageImpl<>(slice, pageable, ranked.size()));
        return withGains(summaries, impacts, current, focusSkills);
    }

    private PageResponse<ProductMatchResponse> withGains(PageResponse<ProductSummaryResponse> summaries,
                                                         Map<Long, List<ProductSkillImpact>> impacts,
                                                         Map<Long, List<Integer>> current,
                                                         Set<String> focusSkills) {
        List<ProductMatchResponse> content = summaries.content().stream()
                .map(summary -> {
                    List<SkillGainResponse> gains = skillProfileService.projectGains(current,
                            impacts.getOrDefault(summary.id(), List.of()));
                    return new ProductMatchResponse(summary, fitScore(gains, focusSkills), gains);
                })
                .toList();
        return new PageResponse<>(content, summaries.page(), summaries.size(),
                summaries.totalElements(), summaries.totalPages());
    }

    // chỉ số tác động (kèm skill) của nhiều sản phẩm trong 1 câu
    private Map<Long, List<ProductSkillImpact>> impactsByProduct(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productSkillImpactRepository.findAllWithSkillByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(impact -> impact.getProduct().getId()));
    }

    // tổng mức tăng vào các nhóm đang lọc (không lọc = mọi nhóm), làm tròn 1 chữ số
    private double fitScore(List<SkillGainResponse> gains, Set<String> focusSkills) {
        double total = gains.stream()
                .filter(gain -> focusSkills.isEmpty() || focusSkills.contains(gain.skillCode()))
                .mapToDouble(SkillGainResponse::gain)
                .sum();
        return BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
