package com.brainblocks.backend.controller;

import com.brainblocks.backend.dto.request.AssignProductRequest;
import com.brainblocks.backend.dto.request.ChildProfileRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.ChildProductResponse;
import com.brainblocks.backend.dto.response.ChildProfileResponse;
import com.brainblocks.backend.service.child.ChildProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// hồ sơ trẻ chỉ dành cho khách hàng; quyền sở hữu từng hồ sơ được kiểm tra ở ChildProfileAccessGuard
@RestController
@RequestMapping("/api/children")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class ChildProfileController {
    private final ChildProfileService childProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChildProfileResponse>>> getMyChildren() {
        return ResponseEntity.ok(ApiResponse.success(childProfileService.getMyChildren()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChildProfileResponse>> createChild(
            @Valid @RequestBody ChildProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Child profile created", childProfileService.createChild(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChildProfileResponse>> getChild(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(childProfileService.getChild(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChildProfileResponse>> updateChild(
            @PathVariable Long id,
            @Valid @RequestBody ChildProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Child profile updated", childProfileService.updateChild(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteChild(@PathVariable Long id) {
        childProfileService.deleteChild(id);
        return ResponseEntity.ok(ApiResponse.success("Child profile deleted", null));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<ApiResponse<List<ChildProductResponse>>> getChildProducts(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(childProfileService.getChildProducts(id)));
    }

    @PostMapping("/{id}/products")
    public ResponseEntity<ApiResponse<ChildProductResponse>> assignProduct(
            @PathVariable Long id,
            @Valid @RequestBody AssignProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product assigned", childProfileService.assignProduct(id, request)));
    }

    @DeleteMapping("/{id}/products/{childProductId}")
    public ResponseEntity<ApiResponse<Void>> removeProduct(
            @PathVariable Long id,
            @PathVariable Long childProductId) {
        childProfileService.removeProduct(id, childProductId);
        return ResponseEntity.ok(ApiResponse.success("Product removed", null));
    }
}
