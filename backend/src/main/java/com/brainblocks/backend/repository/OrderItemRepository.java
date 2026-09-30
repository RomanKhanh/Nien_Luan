package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.OrderItem;
import com.brainblocks.backend.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // khách chỉ được đánh giá sản phẩm đã nhận (đơn DELIVERED có chứa sản phẩm đó)
    @Query("""
            select (count(oi) > 0) from OrderItem oi
            where oi.order.customer.id = :customerId
              and oi.product.id = :productId
              and oi.order.status = :status
            """)
    boolean existsPurchase(@Param("customerId") Long customerId,
                           @Param("productId") Long productId,
                           @Param("status") OrderStatus status);

    // số lượng đã bán, không tính đơn bị loại trừ (đơn đã hủy)
    @Query("""
            select coalesce(sum(oi.quantity), 0) from OrderItem oi
            where oi.product.id = :productId and oi.order.status <> :excluded
            """)
    long sumSoldQuantity(@Param("productId") Long productId, @Param("excluded") OrderStatus excluded);

    // sản phẩm bán chạy cho thống kê
    @Query("""
            select oi.product.id as productId, oi.product.name as productName,
                   sum(oi.quantity) as quantity, sum(oi.unitPrice * oi.quantity) as revenue
            from OrderItem oi
            where oi.order.status <> :excluded
            group by oi.product.id, oi.product.name
            order by sum(oi.quantity) desc, oi.product.id
            """)
    List<ProductSales> findTopSelling(@Param("excluded") OrderStatus excluded, Pageable pageable);

    interface ProductSales {
        Long getProductId();
        String getProductName();
        Long getQuantity();
        BigDecimal getRevenue();
    }
}
