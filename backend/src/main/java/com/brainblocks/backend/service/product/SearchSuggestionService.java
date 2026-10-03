package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.response.product.SearchSuggestionResponse;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductRepository.ProductThumbnail;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.util.SearchTextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gợi ý khi đang gõ ô tìm kiếm: tên sản phẩm, danh mục, nhóm kỹ năng khớp với chữ đã gõ.
 * So khớp trên chữ đã bỏ dấu (SearchTextUtils) nên "lap rap" ra "Bộ lắp ráp…", nhưng trả về tên gốc có dấu.
 */
@Service
@RequiredArgsConstructor
public class SearchSuggestionService {
    static final int MAX_PRODUCTS = 6;
    static final int MAX_CATEGORIES = 3;
    static final int MAX_SKILLS = 2;
    // số sản phẩm lấy từ DB để xếp hạng lại trong bộ nhớ
    private static final int PRODUCT_CANDIDATES = 200;
    private static final int MAX_QUERY_LENGTH = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SkillRepository skillRepository;

    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> suggest(String rawQuery) {
        String query = SearchTextUtils.normalize(rawQuery);
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        String[] tokens = query.split(" ");
        List<SearchSuggestionResponse> result = new ArrayList<>();
        result.addAll(suggestProducts(query, tokens));
        result.addAll(best(categoryRepository.findAll(), Category::getName, query, tokens, MAX_CATEGORIES)
                .stream().map(c -> SearchSuggestionResponse.category(c.getId(), c.getName())).toList());
        result.addAll(best(skillRepository.findAllByOrderByIdAsc(), Skill::getName, query, tokens, MAX_SKILLS)
                .stream().map(s -> SearchSuggestionResponse.skill(s.getCode(), s.getName())).toList());
        return result;
    }

    private List<SearchSuggestionResponse> suggestProducts(String query, String[] tokens) {
        // lọc thô trong DB theo từ dài nhất (search_text gồm tên + mô tả), rồi chỉ giữ món khớp ở tên
        String longest = Arrays.stream(tokens).max(Comparator.comparingInt(String::length)).orElse(query);
        List<Product> candidates = productRepository.findSuggestionCandidates(
                "%" + ProductSpecifications.escapeLike(longest) + "%", PageRequest.of(0, PRODUCT_CANDIDATES));
        List<Product> products = best(candidates, Product::getName, query, tokens, MAX_PRODUCTS);
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, String> thumbnails = productRepository.findThumbnails(products.stream().map(Product::getId).toList())
                .stream().collect(Collectors.toMap(ProductThumbnail::getProductId, ProductThumbnail::getUrl, (a, b) -> a));
        return products.stream()
                .map(p -> SearchSuggestionResponse.product(p.getId(), p.getName(), thumbnails.get(p.getId()), p.getPrice()))
                .toList();
    }

    // giữ các mục có tên khớp, xếp khớp tốt trước; cùng mức thì chỗ khớp gần đầu tên hơn, rồi tên ngắn hơn
    private static <T> List<T> best(List<T> items, Function<T, String> name, String query, String[] tokens, int limit) {
        record Ranked<T>(T item, int rank, int position, String name) {
        }
        return items.stream()
                .map(item -> {
                    String normalized = SearchTextUtils.normalize(name.apply(item));
                    int position = normalized == null ? -1 : normalized.indexOf(query);
                    return new Ranked<>(item, rank(normalized, query, tokens),
                            position < 0 ? Integer.MAX_VALUE : position, name.apply(item));
                })
                .filter(r -> r.rank() >= 0)
                .sorted(Comparator.<Ranked<T>>comparingInt(Ranked::rank)
                        .thenComparingInt(Ranked::position)
                        .thenComparingInt(r -> r.name().length())
                        .thenComparing(Ranked::name))
                .limit(limit)
                .map(Ranked::item)
                .toList();
    }

    /**
     * Mức khớp của tên (đã bỏ dấu) với chữ đã gõ, càng nhỏ càng tốt, -1 = không khớp:
     * 0 tên bắt đầu bằng chữ gõ, 1 một từ trong tên bắt đầu bằng chữ gõ, 2 chữ gõ nằm giữa tên,
     * 3 mọi từ gõ đều có trong tên (không liền nhau). Gõ 1 ký tự thì chỉ nhận khớp đầu từ, tránh gợi ý tràn lan.
     */
    static int rank(String name, String query, String[] tokens) {
        if (name == null) {
            return -1;
        }
        if (name.startsWith(query)) {
            return 0;
        }
        if (name.contains(" " + query)) {
            return 1;
        }
        if (query.length() < 2) {
            return -1;
        }
        if (name.contains(query)) {
            return 2;
        }
        if (tokens.length > 1 && Arrays.stream(tokens).allMatch(name::contains)) {
            return 3;
        }
        return -1;
    }
}
