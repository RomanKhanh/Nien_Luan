package com.brainblocks.backend.config;

import com.brainblocks.backend.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Định kỳ tự hủy các đơn MoMo quá hạn thanh toán (UnpaidOrderPolicy). Lập lịch được bật bởi @EnableScheduling
// ở ReturnExpiryJob. Chạy lần đầu sau 1 phút khởi động, sau đó mỗi app.order.unpaid-check-ms (mặc định 10 phút).
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderExpiryJob {
    private final OrderService orderService;

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.order.unpaid-check-ms:600000}")
    public void cancelExpiredUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        int cancelled = 0;
        // mỗi đơn một transaction riêng (gọi qua proxy của OrderService): một đơn lỗi không kéo cả đợt rollback
        for (Long orderId : orderService.findExpiredUnpaidOrderIds(now)) {
            try {
                if (orderService.cancelIfUnpaidExpired(orderId, now)) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not auto-cancel unpaid order {}", orderId, e);
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} MoMo orders that were not paid in time", cancelled);
        }
    }
}
