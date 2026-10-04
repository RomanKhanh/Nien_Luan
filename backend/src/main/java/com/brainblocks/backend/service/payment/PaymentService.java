package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.dto.response.payment.CreatePaymentUrlResponse;
import com.brainblocks.backend.dto.response.payment.PaymentResponse;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Payment;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.PaymentStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.PaymentRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import com.brainblocks.backend.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderAccessGuard orderAccessGuard;
    private final MoMoGateway moMoGateway;
    private final NotificationService notificationService;

    private Map<PaymentMethod, PaymentGateway> gateways() {
        Map<PaymentMethod, PaymentGateway> map = new EnumMap<>(PaymentMethod.class);
        map.put(PaymentMethod.MOMO, moMoGateway);
        return map;
    }

    public PaymentGateway gatewayFor(PaymentMethod method) {
        PaymentGateway gateway = gateways().get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
        return gateway;
    }

    @Transactional
    public CreatePaymentUrlResponse createPaymentUrl(Long orderId, String clientIp) {
        Order order = orderAccessGuard.getOwnedOrder(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot pay for a cancelled order");
        }
        PaymentGateway gateway = gateways().get(order.getPaymentMethod());
        if (gateway == null) {
            throw new IllegalArgumentException("This order is not set up for online payment (COD)");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalArgumentException("This order has already been paid");
        }
        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .status(PaymentStatus.PENDING)
                    .build();
        }
        // số tiền thanh toán = tổng của đơn, đã gồm phí vận chuyển
        payment.setAmount(order.getTotalAmount());
        paymentRepository.save(payment);

        String txnRef = order.getId() + "-" + System.currentTimeMillis();
        return new CreatePaymentUrlResponse(gateway.createPaymentUrl(order, txnRef, clientIp));
    }

    @Transactional
    public boolean handleCallback(PaymentMethod method, Map<String, String> params) {
        PaymentGateway gateway = gatewayFor(method);
        if (!gateway.verifySignature(params)) {
            return false;
        }

        Long orderId = extractOrderId(gateway.extractTxnRef(params));
        // khóa đơn như OrderService.cancelOrder / AdminOrderService.updateStatus: IPN và thao tác hủy chạy lần lượt,
        // IPN tới trước thì đơn đã sang Đã xác nhận và khách không tự hủy được nữa
        Order order = orderRepository.findForUpdateById(orderId).orElse(null);
        Payment payment = order == null ? null : paymentRepository.findByOrderId(orderId).orElse(null);
        if (order == null || payment == null) {
            return false;
        }

        // idempotency: IPN có thể được gọi lại nhiều lần cho cùng 1 giao dịch
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return true;
        }

        // cổng báo thành công nhưng số tiền khác tổng thanh toán của đơn (gồm phí ship): không ghi nhận đã trả
        if (gateway.isSuccess(params) && !amountMatches(order, gateway.extractAmount(params))) {
            log.warn("Payment amount mismatch for order {}: gateway {} vs order total {}", order.getOrderCode(),
                    gateway.extractAmount(params), order.getTotalAmount());
            return false;
        }

        if (gateway.isSuccess(params)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(gateway.extractTransactionId(params));
            payment.setPaidAt(LocalDateTime.now());
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                // tiền đã bị trừ thật (khách trả qua link MoMo mở từ trước khi hủy) nên vẫn ghi nhận SUCCESS cho đúng sổ,
                // không mở lại đơn (kho đã hoàn, hàng đã về giỏ); báo admin hoàn tiền thủ công vì chưa có API hoàn tiền
                notifyRefundNeeded(order, payment);
            }
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return true;
    }

    private void notifyRefundNeeded(Order order, Payment payment) {
        String amount = OrderService.formatMoney(payment.getAmount());
        log.warn("Order {} was paid after being cancelled (transaction {}, {}): refund needed",
                order.getOrderCode(), payment.getTransactionId(), amount);
        // dùng chung loại thông báo hủy đơn để hiện ở badge mục Đơn hàng (thêm loại mới phải sửa check constraint của DB)
        notificationService.notifyAdmins(NotificationType.ORDER_CANCELLED_BY_CUSTOMER,
                "Cần hoàn tiền MoMo cho đơn đã hủy " + order.getOrderCode(),
                "MoMo vừa báo đã thu " + amount + " (mã giao dịch " + payment.getTransactionId()
                        + ") nhưng đơn đã bị hủy trước đó. Hãy hoàn tiền cho khách trên trang quản lý MoMo.",
                "/admin/orders");
        notificationService.notify(order.getCustomer(), NotificationType.ORDER_STATUS,
                "Đơn " + order.getOrderCode() + " sẽ được hoàn tiền",
                "BrainBlocks đã nhận " + amount + " qua MoMo nhưng đơn đã hủy trước đó, "
                        + "nên khoản tiền này sẽ được hoàn lại vào ví MoMo của bạn.",
                "/orders/" + order.getId());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getStatus(Long orderId) {
        orderAccessGuard.getOwnedOrder(orderId); // chỉ để kiểm tra quyền sở hữu
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for this order"));
        return new PaymentResponse(orderId, payment.getStatus().name(), payment.getAmount(),
                payment.getTransactionId(), payment.getPaidAt());
    }

    // so theo VND nguyên, cùng cách làm tròn với số đã gửi cổng thanh toán
    static boolean amountMatches(Order order, String paidAmount) {
        try {
            return paidAmount != null && new BigDecimal(paidAmount.trim())
                    .compareTo(order.getTotalAmount().setScale(0, RoundingMode.HALF_UP)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Long extractOrderId(String txnRef) {
        try {
            return Long.parseLong(txnRef.split("-")[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction reference: " + txnRef);
        }
    }
}