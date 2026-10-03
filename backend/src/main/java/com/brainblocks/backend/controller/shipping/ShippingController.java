package com.brainblocks.backend.controller.shipping;

import com.brainblocks.backend.dto.request.shipping.ShippingQuoteRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.shipping.ShippingQuoteResponse;
import com.brainblocks.backend.service.shipping.ShippingQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class ShippingController {
    private final ShippingQuoteService shippingQuoteService;

    // phí vận chuyển giỏ hàng hiện tại tới tỉnh đã chọn ở trang thanh toán
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<ShippingQuoteResponse>> quote(@Valid @RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shippingQuoteService.quoteCurrentCart(request.provinceCode())));
    }
}
