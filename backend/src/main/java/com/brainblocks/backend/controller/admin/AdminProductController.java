package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.product.ProductRequest;
import com.brainblocks.backend.dto.request.product.UpdateStockRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.product.ProductDetailResponse;
import com.brainblocks.backend.dto.response.product.ProductSummaryResponse;
import com.brainblocks.backend.service.product.AdminProductService;
import com.brainblocks.backend.service.product.ProductSearchCriteria;
import com.brainblocks.backend.service.product.ProductSort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** đã được SecurityConfig giới hạn cho ROLE_ADMIN
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {
    private final AdminProductService adminProductService;

    // gồm cả sản phẩm đã ẩn; sort mặc định newest cho màn quản trị
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, categoryId, null, null, null,
                null, null, false, true, null);
        return ResponseEntity.ok(ApiResponse.success(
                adminProductService.search(criteria, ProductSort.from(sort), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminProductService.getProduct(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", adminProductService.createProduct(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", adminProductService.updateProduct(id, request)));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateStock(
            @PathVariable Long id, @Valid @RequestBody UpdateStockRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock updated", adminProductService.updateStock(id, request)));
    }

    // ẩn sản phẩm (không xóa cứng), xem AdminProductService.deleteProduct
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        adminProductService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product hidden", null));
    }
}
