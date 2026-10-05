package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.voucher.AdminVoucherResponse;
import com.brainblocks.backend.dto.response.voucher.VoucherSummaryResponse;
import com.brainblocks.backend.service.voucher.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** đã được SecurityConfig giới hạn cho ROLE_ADMIN. Admin chỉ xem, không tạo / sửa voucher.
@RestController
@RequestMapping("/api/admin/vouchers")
@RequiredArgsConstructor
public class AdminVoucherController {
    private final VoucherService voucherService;

    // status: AVAILABLE / USED / EXPIRED, bỏ trống = tất cả
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminVoucherResponse>>> getVouchers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getVouchersForAdmin(status, page, size)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<VoucherSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getSummary()));
    }
}
