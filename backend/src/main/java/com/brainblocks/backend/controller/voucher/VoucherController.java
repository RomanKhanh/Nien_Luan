package com.brainblocks.backend.controller.voucher;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.voucher.VoucherResponse;
import com.brainblocks.backend.service.voucher.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getMyVouchers() {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getMyVouchers()));
    }
}
