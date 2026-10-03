package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Payment;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.PaymentStatus;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.PaymentRepository;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
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
        when(orders.findById(5L)).thenReturn(Optional.of(order));
        PaymentRepository payments = mock(PaymentRepository.class);
        when(payments.findByOrderId(5L)).thenReturn(Optional.of(payment));
        PaymentService service = new PaymentService(payments, orders, mock(OrderAccessGuard.class), gateway);

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
}
