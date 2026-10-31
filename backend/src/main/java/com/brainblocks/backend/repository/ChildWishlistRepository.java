package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChildWishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildWishlistRepository extends JpaRepository<ChildWishlistItem, Long> {
    boolean existsByChildProfileIdAndProductId(
            Long childProfileId,
            Long productId
    );

    Optional<ChildWishlistItem> findByChildProfileIdAndProductId(
            Long childProfileId,
            Long productId
    );

    @Query("""
            select item
            from ChildWishlistItem item
            join fetch item.product
            where item.childProfile.id = :childId
            order by item.createdAt desc, item.id desc
            """)
    List<ChildWishlistItem> findAllForChild(
            @Param("childId") Long childId
    );
}
