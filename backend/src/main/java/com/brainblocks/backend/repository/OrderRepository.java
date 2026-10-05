package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product
            left join fetch i.childProfile
            where o.id = :id
            """)
    Optional<Order> findWithItemsById(@Param("id") Long id);

    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product
            left join fetch i.childProfile
            where o.customer.id = :customerId
            order by o.createdAt desc
            """)
    List<Order> findAllWithItemsByCustomerId(
            @Param("customerId") Long customerId
    );

    // Khóa dòng đơn (SELECT ... FOR UPDATE) trước khi đổi trạng thái, để khách hủy và admin đổi trạng thái
    // cùng lúc không đọc cùng một trạng thái cũ (vd hoàn kho 2 lần). Phải gọi TRƯỚC mọi lần đọc đơn khác
    // trong transaction, vì Hibernate không nạp lại entity đã có trong persistence context.
    // Không join fetch: Postgres không cho FOR UPDATE trên phía nullable của outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForUpdateById(@Param("id") Long id);

    // id các đơn theo hình thức thanh toán + trạng thái, đặt trước một mốc (tìm đơn MoMo quá hạn thanh toán)
    @Query("""
            select o.id from Order o
            where o.paymentMethod = :method and o.status = :status and o.createdAt < :before
            """)
    List<Long> findIdsPlacedBefore(@Param("method") PaymentMethod method, @Param("status") OrderStatus status,
                                   @Param("before") LocalDateTime before);

    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // ===== thống kê =====
    @Query("select o.status as status, count(o) as total from Order o group by o.status")
    List<StatusCount> countByStatus();

    // doanh thu = tiền hàng sau khi trừ voucher giảm giá, KHÔNG gồm phí vận chuyển
    // (đơn cũ chưa có subtotal thì tính theo totalAmount, chưa có discountAmount thì coi là 0)
    @Query("select coalesce(sum(coalesce(o.subtotal, o.totalAmount) - coalesce(o.discountAmount, 0)), 0) "
            + "from Order o where o.status = :status")
    BigDecimal sumSubtotalByStatus(@Param("status") OrderStatus status);

    // phí vận chuyển đã thu (trừ phần miễn phí nhờ voucher freeship), thống kê riêng với doanh thu
    @Query("select coalesce(sum(coalesce(o.shippingFee, 0) - coalesce(o.shippingDiscount, 0)), 0) "
            + "from Order o where o.status = :status")
    BigDecimal sumShippingFeeByStatus(@Param("status") OrderStatus status);

    // đơn từ một thời điểm trở đi (không tính đơn hủy), gom theo ngày ở service cho khỏi phụ thuộc hàm date của từng DB
    @Query("""
            select o.createdAt as createdAt,
                   coalesce(o.subtotal, o.totalAmount) - coalesce(o.discountAmount, 0) as subtotal
            from Order o
            where o.createdAt >= :from and o.status <> :excluded
            """)
    List<OrderAmount> findAmountsSince(@Param("from") LocalDateTime from, @Param("excluded") OrderStatus excluded);

    interface StatusCount {
        OrderStatus getStatus();
        Long getTotal();
    }

    // giá trị tiền hàng của một đơn sau voucher giảm giá (không gồm phí vận chuyển)
    interface OrderAmount {
        LocalDateTime getCreatedAt();
        BigDecimal getSubtotal();
    }
}
