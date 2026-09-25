package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Ứng viên gợi ý cho một bé: đang bán, còn hàng, hợp tuổi, bé chưa có.
    // Lấy kèm chỉ số tác động kỹ năng trong 1 câu để chấm điểm không phát sinh N+1.
    @Query("""
            select distinct p from Product p
            left join fetch p.productSkillImpacts psi
            left join fetch psi.skill
            where p.active = true
              and p.stockQuantity > 0
              and p.minAge <= :age and p.maxAge >= :age
              and not exists (
                  select 1 from ChildProduct cp
                  where cp.childProfile.id = :childProfileId and cp.product = p
              )
            """)
    List<Product> findRecommendationCandidates(@Param("childProfileId") Long childProfileId,
                                               @Param("age") int age);

    // ảnh đại diện của nhiều sản phẩm trong 1 câu
    @Query("""
            select pi.product.id as productId, pi.url as url
            from ProductImage pi
            where pi.product.id in :productIds and pi.thumbnail = true
            """)
    List<ProductThumbnail> findThumbnails(@Param("productIds") List<Long> productIds);

    interface ProductThumbnail {
        Long getProductId();

        String getUrl();
    }
}
