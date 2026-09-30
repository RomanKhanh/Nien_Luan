package com.brainblocks.backend.service.complaint;

import com.brainblocks.backend.dto.request.complaint.ComplaintItemRequest;
import com.brainblocks.backend.dto.request.complaint.CreateComplaintRequest;
import com.brainblocks.backend.dto.response.complaint.ComplaintItemResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.entity.ComplaintItem;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.OrderItem;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.order.OrderAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
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
            // đơn chờ xác nhận / đã xác nhận khách tự hủy được; yêu cầu hủy chủ yếu cho đơn đang giao
            ComplaintType.CANCEL, EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING),
            ComplaintType.OTHER, EnumSet.allOf(OrderStatus.class)
    );

    private final ComplaintRepository complaintRepository;
    private final OrderAccessGuard orderAccessGuard;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public ComplaintResponse createComplaint(Long orderId, CreateComplaintRequest request) {
        // đơn của người khác trả 404 như đơn không tồn tại
        Order order = orderAccessGuard.getOwnedOrder(orderId);
        if (!ALLOWED_ORDER_STATUSES.get(request.type()).contains(order.getStatus())) {
            throw new IllegalArgumentException("Cannot send a " + request.type()
                    + " request for an order that is " + order.getStatus());
        }

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

        return toResponse(complaintRepository.save(complaint));
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
                complaint.getHandledAt()
        );
    }
}
