package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Ứng viên gợi ý cho một bé: đang bán, còn hàng, hợp tuổi, bé chưa có.
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

    @Query("""
            select pi.product.id as productId, pi.url as url
            from ProductImage pi
            where pi.product.id in :productIds and pi.thumbnail = true
            """)
    List<ProductThumbnail> findThumbnails(@Param("productIds") List<Long> productIds);

    // ===== phía khách: chỉ sản phẩm đang bán (active), lọc optional bằng :param is null =====
    @Query("""
            select p from Product p
            where p.active = true
              and (:categoryId is null or p.category.id = :categoryId)
              and (:keyword = '' or lower(p.name) like lower(concat('%', :keyword, '%')))
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
              and (:age is null or (p.minAge <= :age and p.maxAge >= :age))
            """)
    Page<Product> searchActiveProducts(@Param("categoryId") Long categoryId,
                                       @Param("keyword") String keyword,
                                       @Param("minPrice") BigDecimal minPrice,
                                       @Param("maxPrice") BigDecimal maxPrice,
                                       @Param("age") Integer age,
                                       Pageable pageable);

    // ===== phía admin: xem cả sản phẩm đã ẩn (active = false) =====
    @Query("""
            select p from Product p
            where (:keyword = '' or lower(p.name) like lower(concat('%', :keyword, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
            """)
    Page<Product> searchAllForAdmin(@Param("keyword") String keyword,
                                    @Param("categoryId") Long categoryId,
                                    Pageable pageable);

    // Nạp Product kèm toàn bộ ảnh trong 1 câu, dùng cho ProductImageService.
    // Thao tác qua collection này (không gọi thẳng ProductImageRepository.delete) để orphanRemoval
    // hoạt động đúng - giống lưu ý trong ChildProfileService.removeProduct: nếu Product đã được load
    // vào persistence context, xóa trực tiếp qua repository con dễ bị cascade ALL lưu lại lúc flush.
    @Query("select p from Product p left join fetch p.productImages where p.id = :productId")
    Optional<Product> findWithImagesById(@Param("productId") Long productId);

    // trừ kho an toàn, dành cho OrderService gọi sau này khi xác nhận đơn (chưa dùng ở ProductService)
    @Modifying
    @Query("""
            update Product p set p.stockQuantity = p.stockQuantity - :quantity
            where p.id = :productId and p.stockQuantity >= :quantity
            """)
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    interface ProductThumbnail {
        Long getProductId();
        String getUrl();
    }
}
