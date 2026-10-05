package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.VoucherReason;
import com.brainblocks.backend.enums.VoucherType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Voucher của một khách hàng (lớp bổ sung ngoài sơ đồ lớp v5). Điều kiện (mức tiền hàng tối thiểu, mức giảm
 * tối đa) chép từ VoucherPolicy lúc tặng, nên đổi cấu hình sau đó không ảnh hưởng voucher đã phát.
 * Trạng thái suy ra: order != null là đã dùng; chưa dùng mà quá expiresAt là hết hạn; còn lại là dùng được.
 */
@Entity
@Table(
        name = "vouchers",
        indexes = {
                @Index(name = "idx_vouchers_customer", columnList = "customer_id"),
                @Index(name = "idx_vouchers_child_profile", columnList = "childProfileId")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoucherReason reason;

    // PERCENT_OFF: số phần trăm (10, 20); AMOUNT_OFF: số tiền giảm; FREESHIP: null
    // tên cột riêng vì "value" là từ khóa của một số DB (H2)
    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal value;

    // tiền hàng (chưa gồm phí ship) tối thiểu để dùng được
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minSubtotal;

    // mức giảm tối đa của voucher %; null = không giới hạn
    @Column(precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    // bé đạt mốc kỹ năng (voucher SKILL_*); không đặt khóa ngoại để vẫn xóa được hồ sơ bé, tên bé chép lại để hiển thị
    private Long childProfileId;

    @Column(length = 100)
    private String childName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // đơn đang dùng voucher; đơn bị hủy thì trả voucher lại (order = null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    private LocalDateTime usedAt;

    public boolean isUsed() {
        return order != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !isUsed() && now.isAfter(expiresAt);
    }
}
