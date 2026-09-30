package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // hiển thị review trên trang sản phẩm - chỉ review chưa bị ẩn, phân trang vì có thể rất nhiều
    Page<Review> findByProductIdAndVisibleTrue(Long productId, Pageable pageable);

    // check trước khi tạo review mới - đúng ràng buộc unique {customer_id, product_id} của entity
    boolean existsByCustomerIdAndProductId(Long customerId, Long productId);

    // đảm bảo khách chỉ sửa/xóa review của chính mình, giống pattern findByIdAndChildProfileId
    Optional<Review> findByIdAndCustomerId(Long id, Long customerId);

    // rating trung bình + tổng số review hiển thị, dùng cho trang chi tiết sản phẩm
    @Query("""
            select coalesce(avg(r.rating), 0) as avgRating, count(r) as totalReviews
            from Review r
            where r.product.id = :productId and r.visible = true
            """)
    ReviewSummary getSummaryByProductId(@Param("productId") Long productId);

    interface ReviewSummary {
        Double getAvgRating();
        Long getTotalReviews();
    }

}
