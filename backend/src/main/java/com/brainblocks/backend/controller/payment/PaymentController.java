package com.brainblocks.backend.controller.payment;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.payment.CreatePaymentUrlResponse;
import com.brainblocks.backend.dto.response.payment.PaymentResponse;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.service.payment.PaymentGateway;
import com.brainblocks.backend.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/{orderId}/create-url")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CreatePaymentUrlResponse>> createUrl(
            @PathVariable Long orderId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.createPaymentUrl(orderId, clientIp(request))));
    }

    // khách được MoMo redirect về sau khi thanh toán - chỉ để hiển thị, KHÔNG dùng để xác nhận đã thanh toán thật
    @GetMapping("/momo-return")
    public ResponseEntity<ApiResponse<Void>> momoReturn(@RequestParam Map<String, String> params) {
        PaymentGateway gateway = paymentService.gatewayFor(PaymentMethod.MOMO);
        boolean valid = gateway.verifySignature(params) && gateway.isSuccess(params);
        return ResponseEntity.ok(valid ? ApiResponse.success("Payment successful", null)
                : ApiResponse.error("Payment failed or invalid signature"));
    }

    // MoMo gọi server-to-server, body dạng JSON - nguồn xác nhận thanh toán thật sự
    @PostMapping("/momo-ipn")
    public ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
        Map<String, String> params = body.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        paymentService.handleCallback(PaymentMethod.MOMO, params);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getStatus(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getStatus(orderId)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}