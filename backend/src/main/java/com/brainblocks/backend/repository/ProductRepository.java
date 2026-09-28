package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Trừ kho nguyên tử: chỉ trừ khi còn đủ hàng, DB khóa dòng trong lúc update nên
    // 2 đơn đặt song song không thể cùng trừ quá số tồn. Trả 0 = không đủ hàng.
    // Không đụng tới entity Product đang nằm trong persistence context (giá trị stockQuantity trong đó có thể cũ).
    @Modifying(flushAutomatically = true)
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity - :quantity
            where p.id = :productId and p.stockQuantity >= :quantity
            """)
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    // Hoàn kho khi hủy đơn, cộng thẳng trong DB để không mất cập nhật khi chạy song song
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity where p.id = :productId")
    int increaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
