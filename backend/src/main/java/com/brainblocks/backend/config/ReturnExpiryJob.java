package com.brainblocks.backend.config;

import com.brainblocks.backend.service.complaint.AdminComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Định kỳ tự từ chối các yêu cầu trả hàng đã duyệt mà khách gửi hàng về trễ hạn (ReturnPolicy).
// Chạy lần đầu sau 1 phút khởi động, sau đó mỗi app.complaint.expiry-check-ms (mặc định 10 phút).
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ReturnExpiryJob {
    private final AdminComplaintService adminComplaintService;

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.complaint.expiry-check-ms:600000}")
    public void expireOverdueReturns() {
        int expired = adminComplaintService.expireOverdueReturns();
        if (expired > 0) {
            log.info("Rejected {} overdue return requests", expired);
        }
    }
}
