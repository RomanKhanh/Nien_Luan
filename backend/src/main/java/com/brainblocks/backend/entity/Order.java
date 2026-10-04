package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.Region;
import com.brainblocks.backend.util.AddressUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 30)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 100)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String receiverPhone;

    // Địa chỉ dạng chuỗi tự do của đơn đặt TRƯỚC khi có địa chỉ 3 cấp; đơn mới để null.
    // Cột cũ là NOT NULL, LegacyOrderMigration gỡ ràng buộc này khi khởi động (ddl-auto=update không tự gỡ).
    @Column(length = 255)
    private String shippingAddress;

    // Địa chỉ 3 cấp (63 tỉnh trước sáp nhập, LocationDirectory): lưu cả mã lẫn TÊN tại thời điểm đặt
    // để đơn vẫn hiển thị đúng nếu dữ liệu địa chính đổi sau này. Null ở đơn cũ.
    private Integer provinceCode;

    @Column(length = 100)
    private String provinceName;

    private Integer districtCode;

    @Column(length = 100)
    private String districtName;

    // null khi huyện không có cấp xã (huyện đảo)
    private Integer wardCode;

    @Column(length = 100)
    private String wardName;

    // số nhà, tên đường
    @Column(length = 255)
    private String addressDetail;

    // tổng thanh toán = subtotal + shippingFee (giữ tên cột cũ để các chỗ đang dùng như MoMo không phải đổi)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Tiền hàng và phí vận chuyển. Đơn đặt trước khi có phí ship: LegacyOrderMigration điền
    // subtotal = totalAmount, shippingFee = 0 lúc khởi động; vẫn đọc qua getSubtotalOrTotal / getShippingFeeOrZero.
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 12, scale = 2)
    private BigDecimal shippingFee;

    // miền giao hàng và số kiện lúc đặt; null ở đơn cũ
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Region shippingZone;

    private Integer parcelCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // true khi khách tự hủy (chỉ được khi đơn còn chờ xác nhận); null ở đơn chưa hủy hoặc do admin hủy.
    // Để null được để ddl-auto=update thêm cột vào bảng đã có dữ liệu; đơn cũ điền ở LegacyOrderMigration.
    private Boolean cancelledByCustomer;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Payment (0..1, chỉ có khi thanh toán online) chỉ map ở phía Payment.order, không map ngược ở đây:
    // phía mappedBy của @OneToOne không lazy được, mỗi đơn load lên sẽ tốn thêm 1 query tìm payment.

    @Builder.Default
    @OneToMany(mappedBy = "order")
    private List<Complaint> complaints = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public BigDecimal getSubtotalOrTotal() {
        return subtotal != null ? subtotal : totalAmount;
    }

    public boolean isCancelledByCustomer() {
        return Boolean.TRUE.equals(cancelledByCustomer);
    }

    public BigDecimal getShippingFeeOrZero() {
        return shippingFee != null ? shippingFee : BigDecimal.ZERO;
    }

    // địa chỉ để hiển thị: "chi tiết, xã, huyện, tỉnh"; đơn cũ (chưa có địa chỉ 3 cấp) trả nguyên chuỗi cũ
    public String fullAddress() {
        if (provinceCode == null) {
            return shippingAddress;
        }
        return AddressUtils.format(addressDetail, wardName, districtName, provinceName);
    }
}
