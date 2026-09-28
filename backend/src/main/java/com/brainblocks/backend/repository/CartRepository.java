package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query("""
            select distinct c from Cart c
            left join fetch c.items i
            left join fetch i.product
            where c.customer.id = :customerId
            """)
    Optional<Cart> findWithItemsByCustomerId(@Param("customerId") Long customerId);

    // Khóa dòng giỏ (SELECT ... FOR UPDATE) khi đặt hàng: 2 request đặt hàng cùng lúc của một khách
    // chạy lần lượt, request sau thấy giỏ đã rỗng thay vì cùng xóa các dòng giỏ rồi văng lỗi 500.
    // Phải gọi TRƯỚC findWithItemsByCustomerId trong cùng transaction (xem OrderRepository.findForUpdateById).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customer.id = :customerId")
    Optional<Cart> findForUpdateByCustomerId(@Param("customerId") Long customerId);
}
