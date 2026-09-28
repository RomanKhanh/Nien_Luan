package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.ComplaintRequest;
import com.brainblocks.backend.dto.request.complaint.HandleComplaintRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.OrderItem;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phản hồi / khiếu nại và yêu cầu đổi - trả - hủy của khách (đề 2.6), admin tiếp nhận và xử lý (đề 2.10, 2.11).
 * Xử lý khiếu nại không tự đổi trạng thái đơn: admin đổi trạng thái đơn ở màn quản lý đơn hàng nếu cần.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {
    private static final int MAX_PAGE_SIZE = 100;

    // loại yêu cầu -> trạng thái đơn được phép gửi
    private static final Map<ComplaintType, Set<OrderStatus>> ALLOWED_ORDER_STATUSES = Map.of(
            // đổi / trả / chất lượng: chỉ sau khi đã nhận hàng
            ComplaintType.RETURN, EnumSet.of(OrderStatus.DELIVERED),
            ComplaintType.EXCHANGE, EnumSet.of(OrderStatus.DELIVERED),
            ComplaintType.QUALITY, EnumSet.of(OrderStatus.DELIVERED),
            // đơn chờ xác nhận / đã xác nhận khách tự hủy được; yêu cầu hủy chủ yếu cho đơn đang giao
            ComplaintType.CANCEL, EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING),
            ComplaintType.OTHER, EnumSet.allOf(OrderStatus.class)
    );

    // RESOLVED và REJECTED là trạng thái cuối
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED_TRANSITIONS = Map.of(
            ComplaintStatus.PENDING, EnumSet.of(ComplaintStatus.PROCESSING, ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED),
            ComplaintStatus.PROCESSING, EnumSet.of(ComplaintStatus.PROCESSING, ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED),
            ComplaintStatus.RESOLVED, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.REJECTED, EnumSet.noneOf(ComplaintStatus.class)
    );

    private final ComplaintRepository complaintRepository;
    private final AdminRepository adminRepository;
    private final OrderAccessGuard orderAccessGuard;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public ComplaintResponse createComplaint(ComplaintRequest request) {
        // đơn của người khác trả 404 như đơn không tồn tại
        Order order = orderAccessGuard.getOwnedOrder(request.orderId());
        OrderItem orderItem = null;
        if (request.orderItemId() != null) {
            orderItem = order.getItems().stream()
                    .filter(item -> item.getId().equals(request.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Order item not found in this order"));
        }
        if (!ALLOWED_ORDER_STATUSES.get(request.type()).contains(order.getStatus())) {
            throw new IllegalArgumentException("Cannot send a " + request.type()
                    + " request for an order that is " + order.getStatus());
        }
        Customer customer = order.getCustomer();
        Complaint complaint = complaintRepository.save(Complaint.builder()
                .order(order)
                .customer(customer)
                .orderItem(orderItem)
                .type(request.type())
                .content(request.content().trim())
                .status(ComplaintStatus.PENDING)
                .build());
        return toResponse(complaint);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints() {
        return complaintRepository.findByCustomerIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId())
                .stream().map(this::toResponse).toList();
    }

    // ===== admin =====

    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> getComplaintsForAdmin(ComplaintStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(complaintRepository.findForAdmin(status, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(Long id) {
        return toResponse(findComplaint(id));
    }

    @Transactional
    public ComplaintResponse handleComplaint(Long id, HandleComplaintRequest request) {
        Complaint complaint = findComplaint(id);
        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus next = request.status();
        if (!ALLOWED_TRANSITIONS.get(current).contains(next)) {
            throw new IllegalArgumentException("Cannot change complaint status from " + current + " to " + next);
        }
        boolean closing = next == ComplaintStatus.RESOLVED || next == ComplaintStatus.REJECTED;
        String response = request.response() == null || request.response().isBlank() ? null : request.response().trim();
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
        return toResponse(complaint);
    }

    private Complaint findComplaint(Long id) {
        return complaintRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
    }

    private ComplaintResponse toResponse(Complaint c) {
        Order order = c.getOrder();
        OrderItem item = c.getOrderItem();
        return new ComplaintResponse(
                c.getId(),
                order.getId(),
                order.getOrderCode(),
                order.getStatus().name(),
                item == null ? null : item.getId(),
                item == null ? null : item.getProduct().getName(),
                c.getCustomer().getFullName(),
                c.getCustomer().getEmail(),
                c.getType().name(),
                c.getContent(),
                c.getStatus().name(),
                c.getResponse(),
                c.getHandledBy() == null ? null : c.getHandledBy().getFullName(),
                c.getCreatedAt(),
                c.getHandledAt());
    }
}
