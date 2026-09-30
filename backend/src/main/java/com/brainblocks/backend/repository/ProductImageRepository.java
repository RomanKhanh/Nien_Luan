package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    @Modifying
    @Query("update ProductImage pi set pi.thumbnail = false where pi.product.id = :productId")
    void clearThumbnail(@Param("productId") Long productId);
}
