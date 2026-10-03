package com.brainblocks.backend.controller.product;

import com.brainblocks.backend.dto.request.review.ReviewRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductDetailResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.dto.response.product.SearchSuggestionResponse;
import com.brainblocks.backend.dto.response.product.SearchSuggestionResponse;
import com.brainblocks.backend.dto.response.review.ReviewResponse;
import com.brainblocks.backend.service.product.ProductSearchCriteria;
import com.brainblocks.backend.service.product.ProductService;
import com.brainblocks.backend.service.product.ProductSort;
import com.brainblocks.backend.service.product.SearchSuggestionService;
import com.brainblocks.backend.service.product.SearchSuggestionService;
import com.brainblocks.backend.service.review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

// GET /api/products/** mở công khai (SecurityConfig); gửi đánh giá cần đăng nhập với vai trò khách hàng
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final ReviewService reviewService;
    private final SearchSuggestionService searchSuggestionService;

    // age = tuổi của bé (lọc đúng tuổi); ageFrom / ageTo = khoảng tuổi (nhóm tuổi trên bộ lọc)
    // skills nhận dạng skills=LOGIC,STEM hoặc lặp lại tham số; sort: relevance | price_asc | price_desc | newest | name
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) Integer ageFrom,
            @RequestParam(required = false) Integer ageTo,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId,
                age != null ? age : ageFrom, age != null ? age : ageTo, normalizeCodes(skills),
                minPrice, maxPrice, inStock, false, null);
        return ResponseEntity.ok(ApiResponse.success(
                productService.search(criteria, ProductSort.from(sort), page, size)));
    }

    // gợi ý khi đang gõ ô tìm kiếm: sản phẩm, danh mục, nhóm kỹ năng; gõ không dấu vẫn ra tên có dấu
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<SearchSuggestionResponse>>> suggest(
            @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.success(searchSuggestionService.suggest(q)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getActiveProduct(id)));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getProductReviews(id, page, size)));
    }

    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review created", reviewService.createReview(id, request)));
    }

    public static List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
