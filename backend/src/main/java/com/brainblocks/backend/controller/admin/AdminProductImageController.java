package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.product.ReorderProductImagesRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.product.ProductImageResponse;
import com.brainblocks.backend.service.product.ProductImageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// /api/admin/** đã được SecurityConfig giới hạn cho ROLE_ADMIN
@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@RequiredArgsConstructor
public class AdminProductImageController {
    private final ProductImageService productImageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> getImages(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(productImageService.getImages(productId)));
    }

    // multipart/form-data, field "file"
    @PostMapping
    public ResponseEntity<ApiResponse<ProductImageResponse>> upload(
            @PathVariable Long productId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded", productImageService.upload(productId, file)));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted", null));
    }

    @PatchMapping("/{imageId}/thumbnail")
    public ResponseEntity<ApiResponse<ProductImageResponse>> setThumbnail(
            @PathVariable Long productId, @PathVariable Long imageId) {
        return ResponseEntity.ok(
                ApiResponse.success("Thumbnail updated", productImageService.setThumbnail(productId, imageId)));
    }

    @PutMapping("/order")
    public ResponseEntity<ApiResponse<List<ProductImageResponse>>> reorder(
            @PathVariable Long productId, @Valid @RequestBody ReorderProductImagesRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Images reordered", productImageService.reorder(productId, request)));
    }
}
