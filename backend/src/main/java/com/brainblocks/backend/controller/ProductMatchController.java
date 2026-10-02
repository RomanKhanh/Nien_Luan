package com.brainblocks.backend.controller;

import com.brainblocks.backend.controller.product.ProductController;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductMatchResponse;
import com.brainblocks.backend.service.product.ProductMatchService;
import com.brainblocks.backend.service.product.ProductSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

// "Hợp với bé": danh sách sản phẩm theo tuổi bé, bỏ món bé đã có, kèm mức tăng điểm kỹ năng dự kiến
@RestController
@RequestMapping("/api/children/{id}/product-matches")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class ProductMatchController {
    private final ProductMatchService productMatchService;

    // cùng bộ lọc với GET /api/products (trừ độ tuổi: luôn theo tuổi bé);
    // sort: fit (mặc định, bổ sung nhiều nhất cho bé) | relevance | price_asc | price_desc | newest | name
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductMatchResponse>>> match(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) List<String> skills,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = ProductMatchService.SORT_FIT) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProductSearchCriteria filters = new ProductSearchCriteria(keyword, categoryId, null, null,
                ProductController.normalizeCodes(skills), minPrice, maxPrice, inStock, false, null);
        return ResponseEntity.ok(ApiResponse.success(productMatchService.match(id, filters, sort, page, size)));
    }
}
