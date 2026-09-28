package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    @EntityGraph(attributePaths = "customer")
    Page<Review> findByProductIdAndVisibleTrue(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    List<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // danh sách cho admin: lọc theo trạng thái hiển thị, null = tất cả
    @EntityGraph(attributePaths = {"customer", "product"})
    @Query("select r from Review r where (:visible is null or r.visible = :visible)")
    Page<Review> findForAdmin(@Param("visible") Boolean visible, Pageable pageable);

    // điểm trung bình + số đánh giá (chỉ đánh giá đang hiển thị) của nhiều sản phẩm trong 1 câu
    @Query("""
            select r.product.id as productId, avg(r.rating) as average, count(r) as total
            from Review r
            where r.visible = true and r.product.id in :productIds
            group by r.product.id
            """)
    List<RatingSummary> summarizeByProductIds(@Param("productIds") List<Long> productIds);

    @Query("""
            select r.rating as rating, count(r) as total
            from Review r
            where r.visible = true and r.product.id = :productId
            group by r.rating
            """)
    List<RatingCount> countByRating(@Param("productId") Long productId);

    interface RatingSummary {
        Long getProductId();
        Double getAverage();
        Long getTotal();
    }

    interface RatingCount {
        Integer getRating();
        Long getTotal();
    }
}
