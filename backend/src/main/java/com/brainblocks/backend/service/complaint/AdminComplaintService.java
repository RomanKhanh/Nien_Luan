package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.UpdateComplaintStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final ComplaintRepository complaintRepository;
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
        Admin admin = adminRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        complaint.setStatus(next);
        complaint.setHandledBy(admin);
        if (response != null) {
            complaint.setResponse(response);
        }
        if (closing) {
            complaint.setHandledAt(LocalDateTime.now());
        }
        // duyệt trả hàng: gỡ đồ chơi khỏi hồ sơ bé nếu bé đã trả hết số món mua cho nó
        if (next == ComplaintStatus.RESOLVED && complaint.getType() == ComplaintType.RETURN) {
            removeReturnedProductsFromChildren(complaint);
        }
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