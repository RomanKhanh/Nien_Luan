package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Hạn gửi hàng về: sau khi admin tiếp nhận yêu cầu đổi / trả hàng (-> PROCESSING), khách có
 * app.complaint.return-window-days ngày (mặc định 7) để gửi hàng về shop. Quá hạn thì ReturnExpiryJob
 * tự chuyển yêu cầu sang REJECTED với returnExpired = true, khách không được đổi / trả nữa.
 */
@Component
public class ReturnPolicy {
    // các loại yêu cầu khách phải gửi hàng về shop
    public static final Set<ComplaintType> SEND_BACK_TYPES = EnumSet.of(ComplaintType.RETURN, ComplaintType.EXCHANGE);

    private final int windowDays;

    public ReturnPolicy(@Value("${app.complaint.return-window-days:7}") int windowDays) {
        if (windowDays < 1) {
            throw new IllegalStateException("app.complaint.return-window-days must be >= 1, got " + windowDays);
        }
        this.windowDays = windowDays;
    }

    public int windowDays() {
        return windowDays;
    }

    // hạn chót gửi hàng về; null nếu không phải yêu cầu đổi / trả hàng hoặc chưa được tiếp nhận
    // (yêu cầu tiếp nhận trước khi có quy định này không có acceptedAt nên không bị tính hạn)
    public LocalDateTime deadline(Complaint complaint) {
        if (!SEND_BACK_TYPES.contains(complaint.getType()) || complaint.getAcceptedAt() == null) {
            return null;
        }
        return complaint.getAcceptedAt().plusDays(windowDays);
    }

    // đang chờ khách gửi hàng về mà đã qua hạn
    public boolean isOverdue(Complaint complaint, LocalDateTime now) {
        LocalDateTime deadline = deadline(complaint);
        return complaint.getStatus() == ComplaintStatus.PROCESSING && deadline != null && now.isAfter(deadline);
    }

    // mốc acceptedAt cũ hơn mốc này là đã quá hạn
    public LocalDateTime overdueCutoff(LocalDateTime now) {
        return now.minusDays(windowDays);
    }
}
