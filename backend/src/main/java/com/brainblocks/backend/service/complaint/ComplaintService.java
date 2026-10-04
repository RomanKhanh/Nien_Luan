package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.ComplaintItemRequest;
import com.brainblocks.backend.dto.request.complaint.CreateComplaintRequest;
import com.brainblocks.backend.dto.response.complaint.ComplaintAttachmentResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintItemResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.entity.ComplaintAttachment;
import com.brainblocks.backend.entity.ComplaintItem;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.OrderItem;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import com.brainblocks.backend.enums.EvidenceKind;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.Role;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ComplaintAttachmentRepository;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComplaintService {
    // loại yêu cầu -> trạng thái đơn được phép gửi
    private static final Map<ComplaintType, Set<OrderStatus>> ALLOWED_ORDER_STATUSES = Map.of(
            // đổi / trả / chất lượng: chỉ sau khi đã nhận hàng
            ComplaintType.RETURN, EnumSet.of(OrderStatus.DELIVERED),
            ComplaintType.EXCHANGE, EnumSet.of(OrderStatus.DELIVERED),
            ComplaintType.QUALITY, EnumSet.of(OrderStatus.DELIVERED),
            // chờ xác nhận: khách tự hủy bằng nút Hủy đơn; đã xác nhận: chỉ hủy qua yêu cầu; đang giao: không hủy được nữa
            ComplaintType.CANCEL, EnumSet.of(OrderStatus.CONFIRMED),
            ComplaintType.OTHER, EnumSet.allOf(OrderStatus.class)
    );

    private static final int NOTIFICATION_PREVIEW_LENGTH = 120;
    // loại yêu cầu bắt buộc kèm video mở hàng (khiếu nại chất lượng chỉ cần ảnh / video minh họa, không bắt buộc)
    private static final Set<ComplaintType> EVIDENCE_REQUIRED_TYPES =
            EnumSet.of(ComplaintType.RETURN, ComplaintType.EXCHANGE);
    private static final int MAX_CONDITION_FILES = 5;

    private final ComplaintRepository complaintRepository;
    private final ComplaintAttachmentRepository attachmentRepository;
    private final ComplaintEvidenceStorage evidenceStorage;
    private final OrderAccessGuard orderAccessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;
    private final ReturnPolicy returnPolicy;
    private final EvidenceRetention evidenceRetention;

    /**
     * @param unboxingVideo  video mở hàng; bắt buộc với đổi / trả hàng (EVIDENCE_REQUIRED_TYPES)
     *                       để xác định hàng hỏng do vận chuyển, giao nhầm hay lý do khác
     * @param conditionFiles ảnh / video minh họa (tình trạng sản phẩm, lỗi chất lượng...), không bắt buộc,
     *                       tối đa MAX_CONDITION_FILES
     */
    @Transactional
    public ComplaintResponse createComplaint(Long orderId, CreateComplaintRequest request,
                                             MultipartFile unboxingVideo, List<MultipartFile> conditionFiles) {
        // đơn của người khác trả 404 như đơn không tồn tại
        Order order = orderAccessGuard.getOwnedOrder(orderId);
        // khách tự hủy lúc chờ xác nhận: hàng đã về giỏ, đơn chưa giao gì nên không còn gì để phản hồi
        if (order.isCancelledByCustomer()) {
            throw new IllegalArgumentException("Cannot send feedback for an order you cancelled yourself");
        }
        if (!ALLOWED_ORDER_STATUSES.get(request.type()).contains(order.getStatus())) {
            throw new IllegalArgumentException("Cannot send a " + request.type()
                    + " request for an order that is " + order.getStatus());
        }

        // kiểm tra hết file trước khi ghi gì xuống đĩa / DB
        boolean hasVideo = unboxingVideo != null && !unboxingVideo.isEmpty();
        if (EVIDENCE_REQUIRED_TYPES.contains(request.type()) && !hasVideo) {
            throw new IllegalArgumentException("An unboxing video is required for exchange and return requests");
        }
        List<MultipartFile> conditions = conditionFiles == null ? List.of()
                : conditionFiles.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (conditions.size() > MAX_CONDITION_FILES) {
            throw new IllegalArgumentException("At most " + MAX_CONDITION_FILES + " condition photos/videos");
        }
        Map<MultipartFile, String> contentTypes = new LinkedHashMap<>();
        if (hasVideo) {
            contentTypes.put(unboxingVideo, evidenceStorage.validate(unboxingVideo, EvidenceKind.UNBOXING_VIDEO));
        }
        conditions.forEach(file -> contentTypes.put(file, evidenceStorage.validate(file, EvidenceKind.CONDITION)));

        List<ComplaintItemRequest> itemRequests = request.items() == null ? List.of() : request.items();
        Map<Long, OrderItem> orderItemsById = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        Complaint complaint = Complaint.builder()
                .order(order)
                .customer(order.getCustomer())
                .type(request.type())
                .content(request.content().trim())
                .status(ComplaintStatus.PENDING)
                .build();

        Set<Long> seenIds = new HashSet<>();
        for (ComplaintItemRequest itemRequest : itemRequests) {
            if (!seenIds.add(itemRequest.orderItemId())) {
                throw new IllegalArgumentException("Duplicate orderItemId: " + itemRequest.orderItemId());
            }
            OrderItem orderItem = orderItemsById.get(itemRequest.orderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException(
                        "Order item does not belong to this order: " + itemRequest.orderItemId());
            }
            if (itemRequest.quantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException(
                        "Quantity complained exceeds purchased quantity for order item " + itemRequest.orderItemId());
            }
            complaint.getItems().add(ComplaintItem.builder()
                    .complaint(complaint)
                    .orderItem(orderItem)
                    .quantity(itemRequest.quantity())
                    .build());
        }

        Complaint saved = complaintRepository.save(complaint);
        // cần id khiếu nại để đặt thư mục; file ghi lỗi hoặc transaction rollback thì storage tự dọn
        contentTypes.forEach((file, contentType) -> saved.getAttachments().add(ComplaintAttachment.builder()
                .complaint(saved)
                .kind(file == unboxingVideo ? EvidenceKind.UNBOXING_VIDEO : EvidenceKind.CONDITION)
                .storedPath(evidenceStorage.store(saved.getId(), file))
                .originalName(originalName(file))
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .build()));
        notificationService.notifyAdmins(NotificationType.NEW_COMPLAINT,
                "Yêu cầu mới cho đơn " + order.getOrderCode(),
                order.getCustomer().getFullName() + ": " + abbreviate(saved.getContent()),
                "/admin/complaints");
        return toResponse(saved);
    }

    /**
     * File bằng chứng để xem / tải: chỉ chủ khiếu nại và admin; người khác nhận 404 như không tồn tại.
     */
    @Transactional(readOnly = true)
    public EvidenceFile loadAttachment(Long complaintId, Long attachmentId) {
        ComplaintAttachment attachment = attachmentRepository.findByIdAndComplaintId(attachmentId, complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        boolean isAdmin = currentUserProvider.getCurrentUser().getRole() == Role.ADMIN;
        boolean isOwner = attachment.getComplaint().getCustomer().getId().equals(currentUserProvider.getCurrentUserId());
        if (!isAdmin && !isOwner) {
            throw new ResourceNotFoundException("Attachment not found");
        }
        Path path = evidenceStorage.resolve(attachment.getStoredPath());
        // đã bị xóa tự động hết hạn giữ (hoặc file mất khỏi đĩa): chỉ còn thông tin, không còn nội dung
        if (attachment.getPurgedAt() != null || !Files.exists(path)) {
            throw new ResourceNotFoundException("Attachment has been deleted");
        }
        return new EvidenceFile(path, attachment.getContentType(), attachment.getOriginalName());
    }

    public record EvidenceFile(Path path, String contentType, String originalName) {
    }

    // tên gốc chỉ để hiển thị; bỏ phần thư mục nếu trình duyệt gửi kèm, cắt cho vừa cột
    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    // nội dung khiếu nại dài thì chỉ lấy đoạn đầu cho thông báo
    private static String abbreviate(String text) {
        return text.length() <= NOTIFICATION_PREVIEW_LENGTH
                ? text
                : text.substring(0, NOTIFICATION_PREVIEW_LENGTH).stripTrailing() + "…";
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints() {
        return complaintRepository.findAllByCustomerId(currentUserProvider.getCurrentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    // dùng khi thao tác trên 1 complaint đơn lẻ (create, getMyComplaints đã fetch join, updateStatus)
    ComplaintResponse toResponse(Complaint complaint) {
        return toResponse(complaint, complaint.getItems());
    }

    // dùng cho list admin: items được batch-fetch sẵn từ ngoài, không đụng vào complaint.getItems() (tránh N+1)
    ComplaintResponse toResponse(Complaint complaint, List<ComplaintItem> complaintItems) {
        List<ComplaintItemResponse> items = complaintItems.stream()
                .sorted(Comparator.comparing(ci -> ci.getOrderItem().getId()))
                .map(ci -> new ComplaintItemResponse(
                        ci.getOrderItem().getId(),
                        ci.getOrderItem().getProduct().getId(),
                        ci.getOrderItem().getProduct().getName(),
                        ci.getQuantity()))
                .toList();

        return new ComplaintResponse(
                complaint.getId(),
                complaint.getOrder().getId(),
                complaint.getOrder().getOrderCode(),
                complaint.getOrder().getStatus().name(),
                complaint.getCustomer().getFullName(),
                complaint.getCustomer().getEmail(),
                complaint.getType().name(),
                complaint.getContent(),
                complaint.getStatus().name(),
                complaint.getResponse(),
                complaint.getHandledBy() == null ? null : complaint.getHandledBy().getFullName(),
                items,
                complaint.getCreatedAt(),
                complaint.getHandledAt(),
                complaint.getAcceptedAt(),
                returnPolicy.deadline(complaint),
                complaint.isReturnExpired(),
                complaint.getAttachments().stream()
                        .sorted(Comparator.comparing(ComplaintAttachment::getKind)
                                .thenComparing(ComplaintAttachment::getId))
                        .map(a -> new ComplaintAttachmentResponse(a.getId(), a.getKind().name(),
                                a.getContentType(), a.getOriginalName(), a.getSizeBytes(), a.getPurgedAt()))
                        .toList(),
                complaint.isEvidenceHold(),
                evidenceRetention.purgeAt(complaint)
        );
    }
}
