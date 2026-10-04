package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Payment;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.PaymentStatus;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.PaymentRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import com.brainblocks.backend.service.order.UnpaidOrderPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Số tiền gửi MoMo = tổng thanh toán của đơn (tiền hàng + phí vận chuyển), và IPN / return chỉ được ghi nhận
 * đã thanh toán khi MoMo báo đúng số đó. Unit test thuần, không gọi MoMo thật.
 */
class MoMoPaymentAmountTest {

    // đơn 100.000 tiền hàng + 26.000 phí ship
    private static Order order() {
        Order order = Order.builder()
                .orderCode("BB-TEST")
                .subtotal(new BigDecimal("100000.00"))
                .shippingFee(new BigDecimal("26000.00"))
                .totalAmount(new BigDecimal("126000.00"))
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.MOMO)
                .build();
        order.setId(5L);
        return order;
    }

    @Test
    void createRequestSendsTotalIncludingShipping() {
        MoMoGateway gateway = new MoMoGateway();
        ReflectionTestUtils.setField(gateway, "partnerCode", "MOMO");
        ReflectionTestUtils.setField(gateway, "accessKey", "access");
        ReflectionTestUtils.setField(gateway, "secretKey", "secret");
        ReflectionTestUtils.setField(gateway, "redirectUrl", "http://localhost/return");
        ReflectionTestUtils.setField(gateway, "ipnUrl", "http://localhost/ipn");

        Map<String, Object> body = gateway.buildCreateRequest(order(), "5-123");
        assertThat(body.get("amount")).isEqualTo("126000");
        assertThat(body.get("orderId")).isEqualTo("5-123");
        assertThat(body.get("signature")).asString().hasSize(64);
    }

    @Test
    void amountMatchesOnlyTheOrderTotal() {
        assertThat(PaymentService.amountMatches(order(), "126000")).isTrue();
        assertThat(PaymentService.amountMatches(order(), "100000")).isFalse(); // chỉ tiền hàng, thiếu phí ship
        assertThat(PaymentService.amountMatches(order(), "126001")).isFalse();
        assertThat(PaymentService.amountMatches(order(), "abc")).isFalse();
        assertThat(PaymentService.amountMatches(order(), null)).isFalse();
    }

    @Test
    void callbackWithWrongAmountIsNotRecordedAsPaid() {
        Order order = order();
        Payment payment = Payment.builder().order(order).amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING).build();
        MoMoGateway gateway = mock(MoMoGateway.class);
        when(gateway.verifySignature(anyMap())).thenReturn(true);
        when(gateway.isSuccess(anyMap())).thenReturn(true);
        when(gateway.extractTxnRef(anyMap())).thenReturn("5-123");
        when(gateway.extractTransactionId(anyMap())).thenReturn("trans-1");
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findForUpdateById(5L)).thenReturn(Optional.of(order));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByOrderId(5L)).thenReturn(Optional.of(payment));
        PaymentService service = new PaymentService(payments, orders, mock(OrderAccessGuard.class), gateway,
                mock(NotificationService.class), new UnpaidOrderPolicy(24));

        // MoMo báo đã thu 100.000 (thiếu phí ship): không ghi nhận
        when(gateway.extractAmount(anyMap())).thenReturn("100000");
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // đúng tổng 126.000: đã thanh toán, đơn được xác nhận
        when(gateway.extractAmount(anyMap())).thenReturn("126000");
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void callbackAfterCancelKeepsOrderCancelledAndAsksForRefund() {
        Order order = order();
        order.setStatus(OrderStatus.CANCELLED);
        Payment payment = Payment.builder().order(order).amount(order.getTotalAmount())
                .status(PaymentStatus.PENDING).build();
        MoMoGateway gateway = mock(MoMoGateway.class);
        when(gateway.verifySignature(anyMap())).thenReturn(true);
        when(gateway.isSuccess(anyMap())).thenReturn(true);
        when(gateway.extractTxnRef(anyMap())).thenReturn("5-123");
        when(gateway.extractTransactionId(anyMap())).thenReturn("trans-1");
        when(gateway.extractAmount(anyMap())).thenReturn("126000");
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findForUpdateById(5L)).thenReturn(Optional.of(order));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByOrderId(5L)).thenReturn(Optional.of(payment));
        NotificationService notifications = mock(NotificationService.class);
        PaymentService service = new PaymentService(payments, orders, mock(OrderAccessGuard.class), gateway,
                notifications, new UnpaidOrderPolicy(24));

        // khách trả qua link MoMo mở trước khi hủy: tiền đã bị trừ nên ghi nhận, nhưng đơn vẫn hủy và admin được báo hoàn tiền
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(notifications).notifyAdmins(eq(NotificationType.ORDER_CANCELLED_BY_CUSTOMER),
                contains("Cần hoàn tiền"), contains("trans-1"), eq("/admin/orders"));

        // IPN gửi lại lần nữa: không báo trùng
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        verify(notifications, times(1)).notifyAdmins(any(), any(), any(), any());
    }
}
