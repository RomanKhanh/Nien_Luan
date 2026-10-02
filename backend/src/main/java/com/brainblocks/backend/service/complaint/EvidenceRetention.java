package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.enums.ComplaintStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Thời hạn giữ file bằng chứng: app.complaint.evidence-retention-days ngày (mặc định 30) kể từ khi yêu cầu
 * ĐÓNG (đã giải quyết / bị từ chối / tự hết hạn), để không mất bằng chứng khi yêu cầu còn đang xử lý.
 * Quá hạn thì EvidencePurgeJob xóa file khỏi đĩa, chỉ giữ bản ghi (vỏ) để thống kê.
 * Admin bật evidenceHold cho yêu cầu đang tranh chấp thì không xóa.
 */
@Component
public class EvidenceRetention {
    public static final Set<ComplaintStatus> CLOSED_STATUSES = EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);

    private final int retentionDays;

    public EvidenceRetention(@Value("${app.complaint.evidence-retention-days:30}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.complaint.evidence-retention-days must be >= 1, got " + retentionDays);
        }
        this.retentionDays = retentionDays;
    }

    public int retentionDays() {
        return retentionDays;
    }

    // ngày file bằng chứng sẽ bị xóa; null nếu chưa đóng, đang được giữ lại hoặc không còn file nào
    public LocalDateTime purgeAt(Complaint complaint) {
        boolean hasFiles = complaint.getAttachments().stream().anyMatch(a -> a.getPurgedAt() == null);
        if (!hasFiles || complaint.isEvidenceHold() || !CLOSED_STATUSES.contains(complaint.getStatus())
                || complaint.getHandledAt() == null) {
            return null;
        }
        return complaint.getHandledAt().plusDays(retentionDays);
    }

    // yêu cầu đóng trước mốc này là đã quá hạn giữ bằng chứng
    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minusDays(retentionDays);
    }
}
