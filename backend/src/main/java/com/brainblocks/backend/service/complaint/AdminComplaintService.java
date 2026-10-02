package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.UpdateComplaintStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminComplaintService {
    private static final int MAX_PAGE_SIZE = 100;

    // chờ tiếp nhận -> tiếp nhận hoặc từ chối; đã tiếp nhận thì chỉ còn đánh dấu đã giải quyết.
    // RESOLVED và REJECTED là trạng thái cuối
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
            ComplaintStatus.PENDING, EnumSet.of(ComplaintStatus.PROCESSING, ComplaintStatus.REJECTED),
            ComplaintStatus.PROCESSING, EnumSet.of(ComplaintStatus.RESOLVED),
            ComplaintStatus.RESOLVED, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.REJECTED, EnumSet.noneOf(ComplaintStatus.class)
    );

    // tên loại yêu cầu trong thông báo gửi khách
    private static final Map<ComplaintType, String> TYPE_LABELS = Map.of(
            ComplaintType.RETURN, "Yêu cầu trả hàng",
            ComplaintType.EXCHANGE, "Yêu cầu đổi hàng",
            ComplaintType.CANCEL, "Yêu cầu hủy đơn",
            ComplaintType.QUALITY, "Khiếu nại chất lượng",
            ComplaintType.OTHER, "Phản hồi"
    );

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;
    private final ReturnPolicy returnPolicy;
    private final EvidenceRetention evidenceRetention;
    private final ComplaintEvidenceStorage evidenceStorage;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintItemRepository complaintItemRepository;
    private final AdminRepository adminRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ComplaintService complaintService;
    private final OrderItemRepository orderItemRepository;
    private final ChildProductRepository childProductRepository;
    private final SkillProfileService skillProfileService;

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> getComplaints(ComplaintStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Complaint> complaints = status == null
                ? complaintRepository.findAll(pageable)
                : complaintRepository.findByStatus(status, pageable);

        // batch-fetch items cho cả trang trong 1 câu, thay vì để mỗi complaint tự lazy-load (N+1)
        List<Long> complaintIds = complaints.getContent().stream().map(Complaint::getId).toList();
        Map<Long, List<ComplaintItem>> itemsByComplaintId = complaintIds.isEmpty()
                ? Map.of()
                : complaintItemRepository.findAllWithOrderItemAndProductByComplaintIdIn(complaintIds).stream()
                .collect(Collectors.groupingBy(ci -> ci.getComplaint().getId()));

        return PageResponse.of(complaints,
                c -> complaintService.toResponse(c, itemsByComplaintId.getOrDefault(c.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(Long complaintId) {
        return complaintService.toResponse(findComplaint(complaintId));
    }

    @Transactional
    public ComplaintResponse updateStatus(Long complaintId, UpdateComplaintStatusRequest request) {
        Complaint complaint = findComplaint(complaintId);

        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus next = request.status();
        if (!ALLOWED_TRANSITIONS.get(current).contains(next)) {
            throw new IllegalArgumentException("Cannot change complaint status from " + current + " to " + next);
        }
        boolean closing = next == ComplaintStatus.RESOLVED || next == ComplaintStatus.REJECTED;
        String response = request.response() == null || request.response().isBlank() ? null : request.response().trim();
        // đóng khiếu nại phải có phản hồi gửi khách
        if (closing && response == null) {
            throw new IllegalArgumentException("A response to the customer is required to close the complaint");
        }
        LocalDateTime now = LocalDateTime.now();
        // quá hạn gửi hàng về: không được duyệt hoàn tất nữa, ReturnExpiryJob sẽ tự từ chối (admin vẫn từ chối tay được)
        if (next == ComplaintStatus.RESOLVED && returnPolicy.isOverdue(complaint, now)) {
            throw new IllegalArgumentException("The return window ended at "
                    + DEADLINE_FORMAT.format(returnPolicy.deadline(complaint))
                    + "; this request will be rejected automatically");
        }
        Admin admin = adminRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        complaint.setStatus(next);
        complaint.setHandledBy(admin);
        if (response != null) {
            complaint.setResponse(response);
        }
        if (next == ComplaintStatus.PROCESSING) {
            complaint.setAcceptedAt(now);
        }
        if (closing) {
            complaint.setHandledAt(now);
        }
        // duyệt trả hàng: gỡ đồ chơi khỏi hồ sơ bé nếu bé đã trả hết số món mua cho nó
        if (next == ComplaintStatus.RESOLVED && complaint.getType() == ComplaintType.RETURN) {
            removeReturnedProductsFromChildren(complaint);
        }
        notifyCustomer(complaint, next, response);
        return complaintService.toResponse(complaint);
    }

    private void notifyCustomer(Complaint complaint, ComplaintStatus status, String response) {
        String subject = TYPE_LABELS.get(complaint.getType()) + " cho đơn " + complaint.getOrder().getOrderCode();
        String title = switch (status) {
            case PROCESSING -> "Yêu cầu đã được tiếp nhận";
            case RESOLVED -> "Yêu cầu đã được giải quyết";
            case REJECTED -> "Yêu cầu bị từ chối";
            case PENDING -> null;
        };
        if (title == null) {
            return;
        }
        String message = response == null ? subject + "." : subject + ". Phản hồi: " + response;
        LocalDateTime deadline = returnPolicy.deadline(complaint);
        if (status == ComplaintStatus.PROCESSING && deadline != null) {
            message += " Vui lòng gửi hàng về cửa hàng trước " + DEADLINE_FORMAT.format(deadline)
                    + " (" + returnPolicy.windowDays() + " ngày kể từ khi yêu cầu được duyệt).";
        }
        notificationService.notify(complaint.getCustomer(), NotificationType.COMPLAINT_UPDATED, title, message,
                "/feedback");
    }

    /**
     * Tự từ chối các yêu cầu đổi / trả hàng đã được tiếp nhận mà khách chưa gửi hàng về trong hạn
     * (ReturnExpiryJob gọi định kỳ). Đánh dấu returnExpired để hiện rõ là do phía khách trễ hạn;
     * yêu cầu đã đóng nên khách không được đổi / trả nữa.
     *
     * @return số yêu cầu đã bị từ chối do quá hạn
     */
    @Transactional
    public int expireOverdueReturns() {
        LocalDateTime now = LocalDateTime.now();
        List<Complaint> overdue = complaintRepository.findAcceptedBefore(
                ReturnPolicy.SEND_BACK_TYPES, ComplaintStatus.PROCESSING, returnPolicy.overdueCutoff(now));
        for (Complaint complaint : overdue) {
            String deadline = DEADLINE_FORMAT.format(returnPolicy.deadline(complaint));
            String label = TYPE_LABELS.get(complaint.getType());
            complaint.setStatus(ComplaintStatus.REJECTED);
            complaint.setReturnExpired(true);
            complaint.setHandledAt(now);
            complaint.setResponse("Quá hạn gửi hàng về: cửa hàng chưa nhận được hàng trong "
                    + returnPolicy.windowDays() + " ngày kể từ khi yêu cầu được duyệt (hạn " + deadline
                    + "), nên không thể đổi / trả nữa.");
            notificationService.notify(complaint.getCustomer(), NotificationType.COMPLAINT_UPDATED,
                    label + " đã hết hạn",
                    label + " cho đơn " + complaint.getOrder().getOrderCode()
                            + " đã tự đóng vì hàng chưa được gửi về trước " + deadline + ".",
                    "/feedback");
        }
        return overdue.size();
    }

    /**
     * Xóa khỏi đĩa file bằng chứng của các yêu cầu đã đóng quá thời hạn giữ (EvidenceRetention), trừ yêu cầu
     * admin đã bật giữ lại. Bản ghi file vẫn giữ (đánh dấu purgedAt) để thống kê. EvidencePurgeJob gọi định kỳ.
     *
     * @return số file đã xóa
     */
    @Transactional
    public int purgeExpiredEvidence() {
        LocalDateTime now = LocalDateTime.now();
        List<ComplaintAttachment> expired = attachmentRepository.findPurgeable(
                EvidenceRetention.CLOSED_STATUSES, evidenceRetention.cutoff(now));
        for (ComplaintAttachment attachment : expired) {
            evidenceStorage.delete(attachment.getStoredPath());
            attachment.setPurgedAt(now);
        }
        return expired.size();
    }

    // admin giữ lại bằng chứng của một yêu cầu (đang tranh chấp...) để không bị xóa tự động; bỏ giữ thì tính hạn như thường
    @Transactional
    public ComplaintResponse setEvidenceHold(Long complaintId, boolean hold) {
        Complaint complaint = findComplaint(complaintId);
        complaint.setEvidenceHold(hold);
        return complaintService.toResponse(complaint);
    }

    private void removeReturnedProductsFromChildren(Complaint complaint) {
        // gom các bé bị gỡ đồ để mỗi bé chỉ tính lại hồ sơ kỹ năng 1 lần
        Map<Long, ChildProfile> changedChildren = new LinkedHashMap<>();
        for (ComplaintItem complaintItem : complaint.getItems()) {
            OrderItem orderItem = complaintItem.getOrderItem();
            ChildProfile child = orderItem.getChildProfile();
            if (child == null) {
                continue; // dòng hàng không mua cho bé nào
            }
            Long productId = orderItem.getProduct().getId();

            // cộng dồn mọi đơn: bé mua 3 món ở 2 đơn, trả 1 thì vẫn còn đồ chơi này
            long bought = orderItemRepository.sumQuantityByChildAndProductAndOrderStatus(
                    child.getId(), productId, OrderStatus.DELIVERED);
            long returned = complaintItemRepository.sumQuantityByChildAndProductAndComplaint(
                    child.getId(), productId, ComplaintType.RETURN, ComplaintStatus.RESOLVED);
            if (returned < bought) {
                continue;
            }

            childProductRepository.findByChildProfileIdAndProductId(child.getId(), productId)
                    // MANUAL là phụ huynh tự thêm (bé có sẵn món này), trả hàng không đụng tới
                    .filter(childProduct -> childProduct.getSource() == ProductSource.PURCHASED)
                    .ifPresent(childProduct -> {
                        // gỡ khỏi collection để orphanRemoval xóa, giống ChildProfileService.removeProduct
                        child.getChildProducts().remove(childProduct);
                        changedChildren.put(child.getId(), child);
                    });
        }
        changedChildren.values().forEach(skillProfileService::recalculateFor);
    }

    private Complaint findComplaint(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
    }

}