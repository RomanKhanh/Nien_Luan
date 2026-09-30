package com.brainblocks.backend.service.payment;

import com.brainblocks.backend.dto.response.payment.CreatePaymentUrlResponse;
import com.brainblocks.backend.dto.response.payment.PaymentResponse;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Payment;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.enums.PaymentStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.PaymentRepository;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderAccessGuard orderAccessGuard;
    private final MoMoGateway moMoGateway;

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
                    .amount(order.getTotalAmount())
                    .status(PaymentStatus.PENDING)
                    .build();
        }
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
        Order order = orderRepository.findById(orderId).orElse(null);
        Payment payment = order == null ? null : paymentRepository.findByOrderId(orderId).orElse(null);
        if (order == null || payment == null) {
            return false;
        }

        // idempotency: IPN có thể được gọi lại nhiều lần cho cùng 1 giao dịch
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return true;
        }

        if (gateway.isSuccess(params)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransactionId(gateway.extractTransactionId(params));
            payment.setPaidAt(LocalDateTime.now());
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.CONFIRMED);
            }
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getStatus(Long orderId) {
        orderAccessGuard.getOwnedOrder(orderId); // chỉ để kiểm tra quyền sở hữu
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for this order"));
        return new PaymentResponse(orderId, payment.getStatus().name(), payment.getAmount(),
                payment.getTransactionId(), payment.getPaidAt());
    }

    private Long extractOrderId(String txnRef) {
        try {
            return Long.parseLong(txnRef.split("-")[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid transaction reference: " + txnRef);
        }
    }
}