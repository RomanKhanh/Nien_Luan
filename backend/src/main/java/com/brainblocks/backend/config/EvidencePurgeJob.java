package com.brainblocks.backend.config;

import com.brainblocks.backend.service.complaint.AdminComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Mỗi ngày (mặc định 3:30 sáng) xóa file bằng chứng của các yêu cầu đã đóng quá thời hạn giữ (EvidenceRetention).
// Lập lịch được bật bởi @EnableScheduling ở ReturnExpiryJob.
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidencePurgeJob {
    private final AdminComplaintService adminComplaintService;

    @Scheduled(cron = "${app.complaint.evidence-purge-cron:0 30 3 * * *}")
    public void purgeExpiredEvidence() {
        int purged = adminComplaintService.purgeExpiredEvidence();
        if (purged > 0) {
            log.info("Purged {} expired complaint evidence files", purged);
        }
    }
}
