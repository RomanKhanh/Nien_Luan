package com.brainblocks.backend.controller.wishlist;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.wishlist.ChildWishlistResponse;
import com.brainblocks.backend.service.wishlist.ChildWishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/children/{childId}/wishlist")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class ChildWishlistController {
    private final ChildWishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChildWishlistResponse>>> getWishlist(
            @PathVariable Long childId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(wishlistService.getWishlist(childId))
        );
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> addProduct(
            @PathVariable Long childId,
            @PathVariable Long productId
    ) {
        wishlistService.addProduct(childId, productId);

        return ResponseEntity.ok(
                ApiResponse.success("Product saved to wishlist", null)
        );
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeProduct(
            @PathVariable Long childId,
            @PathVariable Long productId
    ) {
        wishlistService.removeProduct(childId, productId);

        return ResponseEntity.ok(
                ApiResponse.success("Product removed from wishlist", null)
        );
    }
}
