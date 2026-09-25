package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
// đặt tên constraint để ddl-auto=update thêm được vào bảng đã tồn tại (unique = true trên @Column thì không)
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_transaction_id", columnNames = "transactionId")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // mã giao dịch do VNPay/MoMo trả về sau, lúc mới tạo còn null.
    // unique (uk_payments_transaction_id) để callback bắn 2 lần không ghi trùng; nhiều dòng null vẫn hợp lệ
    @Column(length = 100)
    private String transactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // null khi chưa thanh toán thành công
    private LocalDateTime paidAt;
}
