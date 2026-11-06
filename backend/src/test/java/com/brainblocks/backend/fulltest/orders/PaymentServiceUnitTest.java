package com.brainblocks.backend.fulltest.orders;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Payment;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.PaymentStatus;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.PaymentRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import com.brainblocks.backend.service.order.UnpaidOrderPolicy;
import com.brainblocks.backend.service.payment.MoMoGateway;
import com.brainblocks.backend.service.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No Spring context and no network: actual payment service with isolated collaborators. */
class PaymentServiceUnitTest {
    private PaymentRepository payments;
    private OrderRepository orders;
    private OrderAccessGuard access;
    private MoMoGateway gateway;
    private NotificationService notifications;
    private PaymentService service;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        orders = mock(OrderRepository.class);
        access = mock(OrderAccessGuard.class);
        gateway = mock(MoMoGateway.class);
        notifications = mock(NotificationService.class);
        service = new PaymentService(payments, orders, access, gateway, notifications, new UnpaidOrderPolicy(24));
        order = Order.builder().id(41L).orderCode("BB-ORDERS-UNIT")
                .subtotal(new BigDecimal("100000.00")).shippingFee(new BigDecimal("26000.00"))
                .totalAmount(new BigDecimal("126000.00")).paymentMethod(PaymentMethod.MOMO)
                .status(OrderStatus.PENDING).createdAt(LocalDateTime.now()).build();
        payment = Payment.builder().order(order).amount(order.getTotalAmount()).status(PaymentStatus.PENDING).build();
    }

    private void callbackSetup() {
        when(gateway.verifySignature(anyMap())).thenReturn(true);
        when(gateway.isSuccess(anyMap())).thenReturn(true);
        when(gateway.extractTxnRef(anyMap())).thenReturn("41-attempt");
        when(gateway.extractTransactionId(anyMap())).thenReturn("txn-41");
        when(gateway.extractAmount(anyMap())).thenReturn("126000");
        when(orders.findForUpdateById(41L)).thenReturn(Optional.of(order));
        when(payments.findByOrderId(41L)).thenReturn(Optional.of(payment));
    }

    @Test
    void createUrlPersistsPendingPaymentForTotalIncludingShipping() {
        when(access.getOwnedOrder(41L)).thenReturn(order);
        when(gateway.createPaymentUrl(eq(order), anyString(), eq("127.0.0.1")))
                .thenReturn("https://example.invalid/momo");
        assertThat(service.createPaymentUrl(41L, "127.0.0.1").paymentUrl()).isEqualTo("https://example.invalid/momo");
        ArgumentCaptor<Payment> captured = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captured.capture());
        assertThat(captured.getValue().getOrder()).isSameAs(order);
        assertThat(captured.getValue().getAmount()).isEqualByComparingTo("126000");
        assertThat(captured.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(gateway).createPaymentUrl(eq(order), startsWith("41-"), eq("127.0.0.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COD", "CANCELLED", "SUCCESS", "EXPIRED"})
    void forbiddenPaymentUrlNeverCallsGateway(String reason) {
        when(access.getOwnedOrder(41L)).thenReturn(order);
        switch (reason) {
            case "COD" -> order.setPaymentMethod(PaymentMethod.COD);
            case "CANCELLED" -> order.setStatus(OrderStatus.CANCELLED);
            case "EXPIRED" -> order.setCreatedAt(LocalDateTime.now().minusHours(25));
            case "SUCCESS" -> {
                payment.setStatus(PaymentStatus.SUCCESS);
                when(payments.findByOrderId(41L)).thenReturn(Optional.of(payment));
            }
            default -> throw new AssertionError(reason);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> service.createPaymentUrl(41L, "127.0.0.1"));
        verifyNoInteractions(gateway);
        verify(payments, never()).save(any());
    }

    @Test
    void invalidSignatureIsRejectedBeforeAnyDatabaseAccess() {
        when(gateway.verifySignature(anyMap())).thenReturn(false);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of("signature", "bad"))).isFalse();
        verifyNoInteractions(orders, payments, notifications);
        verify(gateway, never()).extractTxnRef(anyMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000", "126001", "-126000", "0", "abc", ""})
    void successfulIpnWithWrongOrMalformedAmountCannotMarkPaid(String amount) {
        callbackSetup();
        when(gateway.extractAmount(anyMap())).thenReturn(amount);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getTransactionId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(notifications);
    }

    @Test
    void missingIpnAmountCannotMarkPaid() {
        callbackSetup();
        when(gateway.extractAmount(anyMap())).thenReturn(null);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void validIpnMarksPaidConfirmsOrderAndDuplicateDoesNotRewriteTransaction() {
        callbackSetup();
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getTransactionId()).isEqualTo("txn-41");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        LocalDateTime paidAt = payment.getPaidAt();
        when(gateway.extractTransactionId(anyMap())).thenReturn("different-txn");
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getPaidAt()).isEqualTo(paidAt);
        assertThat(payment.getTransactionId()).isEqualTo("txn-41");
        verify(gateway, times(1)).extractTransactionId(anyMap());
    }

    @Test
    void failedGatewayResultMarksPaymentFailedWithoutConfirmingOrder() {
        callbackSetup();
        when(gateway.isSuccess(anyMap())).thenReturn(false);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void unknownOrderCallbackIsRejected() {
        callbackSetup();
        when(orders.findForUpdateById(41L)).thenReturn(Optional.empty());
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isFalse();
        verifyNoInteractions(payments, notifications);
    }

    @Test
    void callbackWithoutCreatedPaymentIsRejected() {
        callbackSetup();
        when(payments.findByOrderId(41L)).thenReturn(Optional.empty());
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void latePaidCallbackKeepsCancelledOrderAndNotifiesRefundOnlyOnce() {
        callbackSetup();
        order.setStatus(OrderStatus.CANCELLED);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(service.handleCallback(PaymentMethod.MOMO, Map.of())).isTrue();
        verify(notifications, times(1)).notifyAdmins(any(), anyString(), contains("txn-41"), eq("/admin/orders"));
        verify(notifications, times(1)).notify(any(), any(), anyString(), anyString(), eq("/orders/41"));
    }

    @Test
    void realMomoSignatureRejectsMissingForgedAndTamperedCallbacks() throws Exception {
        // The real gateway is used only for local HMAC verification, never createPaymentUrl.
        MoMoGateway realGateway = new MoMoGateway();
        ReflectionTestUtils.setField(realGateway, "accessKey", "orders-access");
        ReflectionTestUtils.setField(realGateway, "secretKey", "orders-secret");
        Map<String, String> params = new HashMap<>(Map.of(
                "amount", "126000", "orderId", "41-attempt", "resultCode", "0", "transId", "txn-41"));
        assertThat(realGateway.verifySignature(params)).isFalse();
        params.put("signature", "forged");
        assertThat(realGateway.verifySignature(params)).isFalse();
        String canonical = "accessKey=orders-access&amount=126000&extraData=&message=&orderId=41-attempt"
                + "&orderInfo=&orderType=&partnerCode=&payType=&requestId=&responseTime=&resultCode=0&transId=txn-41";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("orders-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        params.put("signature", HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        assertThat(realGateway.verifySignature(params)).isTrue();
        params.put("amount", "1");
        assertThat(realGateway.verifySignature(params)).isFalse();
    }
}
