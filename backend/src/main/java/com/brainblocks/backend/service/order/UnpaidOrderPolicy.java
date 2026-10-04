package com.brainblocks.backend.service.order;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hạn thanh toán đơn MoMo: đơn MoMo còn chờ xác nhận là đơn chưa trả tiền (MoMo trả thành công thì đơn tự sang
 * Đã xác nhận). Quá app.order.momo-unpaid-timeout-hours giờ (mặc định 24) kể từ lúc đặt mà vẫn chưa trả thì
 * UnpaidOrderExpiryJob tự hủy đơn, hoàn kho và đưa hàng về giỏ.
 */
@Component
public class UnpaidOrderPolicy {
    private final int timeoutHours;

    public UnpaidOrderPolicy(@Value("${app.order.momo-unpaid-timeout-hours:24}") int timeoutHours) {
        if (timeoutHours < 1) {
            throw new IllegalStateException("app.order.momo-unpaid-timeout-hours must be >= 1, got " + timeoutHours);
        }
        this.timeoutHours = timeoutHours;
    }

    public int timeoutHours() {
        return timeoutHours;
    }

    // hạn chót thanh toán; null nếu đơn không phải MoMo đang chờ thanh toán
    public LocalDateTime deadline(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.MOMO || order.getStatus() != OrderStatus.PENDING
                || order.getCreatedAt() == null) {
            return null;
        }
        return order.getCreatedAt().plusHours(timeoutHours);
    }

    public boolean isExpired(Order order, LocalDateTime now) {
        LocalDateTime deadline = deadline(order);
        return deadline != null && !now.isBefore(deadline);
    }

    // đơn đặt trước mốc này là đã quá hạn
    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minusHours(timeoutHours);
    }
}
