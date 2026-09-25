package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // bé được mua cho; null nếu không chọn. Khi đơn DELIVERED, dựa vào đây để tạo ChildProduct PURCHASED.
    // ràng buộc {childProfile.customer = order.customer} phải kiểm tra ở service
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_profile_id")
    private ChildProfile childProfile;

    @Column(nullable = false)
    private int quantity;

    // giá tại thời điểm đặt hàng, không lấy lại từ Product vì giá có thể thay đổi
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
