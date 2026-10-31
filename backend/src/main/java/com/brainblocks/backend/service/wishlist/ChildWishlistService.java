package com.brainblocks.backend.service.wishlist;

import com.brainblocks.backend.dto.response.wishlist.ChildWishlistResponse;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.ChildWishlistItem;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildWishlistRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChildWishlistService {
    private final ChildWishlistRepository wishlistRepository;
    private final ChildProductRepository childProductRepository;
    private final ProductRepository productRepository;
    private final ChildProfileAccessGuard accessGuard;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ChildWishlistResponse> getWishlist(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);

        Set<Long> ownedProductIds = new HashSet<>(
                childProductRepository.findProductIdsByChildProfileId(
                        child.getId()
                )
        );

        return wishlistRepository.findAllForChild(child.getId())
                .stream()
                .map(item -> toResponse(
                        item,
                        child.getAge(),
                        ownedProductIds.contains(item.getProduct().getId())
                ))
                .toList();
    }

    @Transactional
    public void addProduct(Long childId, Long productId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);

        // Tuần tự hóa thao tác sửa wishlist của cùng một bé:
        // hai request thêm cùng lúc không cùng vượt qua bước exists.
        entityManager.lock(child, LockModeType.PESSIMISTIC_WRITE);

        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found")
                );

        // Thêm lại sản phẩm đã lưu vẫn thành công, không tạo dòng trùng.
        if (wishlistRepository.existsByChildProfileIdAndProductId(
                child.getId(), product.getId())) {
            return;
        }

        wishlistRepository.save(
                ChildWishlistItem.builder()
                        .childProfile(child)
                        .product(product)
                        .build()
        );
    }

    @Transactional
    public void removeProduct(Long childId, Long productId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        entityManager.lock(child, LockModeType.PESSIMISTIC_WRITE);

        wishlistRepository.findByChildProfileIdAndProductId(
                        child.getId(), productId
                )
                .ifPresent(wishlistRepository::delete);
    }

    private ChildWishlistResponse toResponse(
            ChildWishlistItem item,
            int childAge,
            boolean alreadyOwned
    ) {
        Product product = item.getProduct();

        boolean ageAppropriate =
                childAge >= product.getMinAge()
                        && childAge <= product.getMaxAge();

        return new ChildWishlistResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getMinAge(),
                product.getMaxAge(),
                product.isActive(),
                product.isActive() && product.getStockQuantity() > 0,
                ageAppropriate,
                alreadyOwned,
                item.getCreatedAt()
        );
    }
}
