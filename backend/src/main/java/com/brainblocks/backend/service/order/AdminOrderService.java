package com.brainblocks.backend.service.order;

import com.brainblocks.backend.dto.request.order.UpdateOrderStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.order.OrderResponse;
import com.brainblocks.backend.dto.response.order.OrderSummaryResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;

    // Luồng trạng thái theo đề: Chờ xác nhận -> Đã xác nhận -> Đang giao -> Đã giao; hủy được khi chưa giao đi
    // (giống điều kiện khách tự hủy ở OrderService.cancelOrder). DELIVERED và CANCELLED là trạng thái cuối.
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    private final OrderRepository orderRepository;
    private final ChildProductRepository childProductRepository;
    private final SkillProfileService skillProfileService;

    private final OrderService orderService;

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getOrders(OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.of(orders, this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return orderService.toOrderResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        // khóa đơn để không chạy chồng với khách đang tự hủy cùng đơn này
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.status();
        if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(newStatus)) {
            throw new IllegalArgumentException("Cannot change order status from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            orderService.restoreStock(order);
        }
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.DELIVERED) {
            createChildProductsForDeliveredOrder(order);
        }

        return orderService.toOrderResponse(order);
    }
    private void createChildProductsForDeliveredOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            ChildProfile child = item.getChildProfile();
            if (child == null) {
                continue;
            }
            boolean alreadyOwned = childProductRepository
                    .existsByChildProfileIdAndProductId(child.getId(), item.getProduct().getId());
            if (alreadyOwned) {
                continue;
            }
            childProductRepository.save(ChildProduct.builder()
                    .childProfile(child)
                    .product(item.getProduct())
                    .source(ProductSource.PURCHASED)
                    .build());
            skillProfileService.recalculateFor(child);
        }
    }
    private OrderSummaryResponse toSummaryResponse(Order order) {
        Customer customer = order.getCustomer();
        return new OrderSummaryResponse(order.getId(), order.getOrderCode(),
                customer.getFullName(), customer.getEmail(), order.getTotalAmount(),
                order.getStatus().name(), order.getPaymentMethod().name(), order.getCreatedAt());
    }

}
