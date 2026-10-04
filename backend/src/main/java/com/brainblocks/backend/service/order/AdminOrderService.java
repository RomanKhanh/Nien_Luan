package com.brainblocks.backend.service.order;

import com.brainblocks.backend.dto.request.order.UpdateOrderStatusRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.order.OrderResponse;
import com.brainblocks.backend.dto.response.order.OrderSummaryResponse;
import com.brainblocks.backend.dto.response.skill.SkillProfileResponse;
import com.brainblocks.backend.dto.response.skill.SkillScoreResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.skill.SkillProfileService;
import com.brainblocks.backend.service.skill.SkillScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;

    // Luồng trạng thái theo đề: Chờ xác nhận -> Đã xác nhận -> Đang giao -> Đã giao; admin hủy được khi chưa giao đi
    // (khách tự hủy chỉ khi còn chờ xác nhận, xem OrderService.cancelOrder). DELIVERED và CANCELLED là trạng thái cuối.
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
    private final NotificationService notificationService;

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
        notifyCustomer(order, newStatus);

        if (newStatus == OrderStatus.DELIVERED) {
            createChildProductsForDeliveredOrder(order);
        }

        return orderService.toOrderResponse(order);
    }

    private void notifyCustomer(Order order, OrderStatus status) {
        String code = order.getOrderCode();
        String[] content = switch (status) {
            case CONFIRMED -> new String[]{"Đơn hàng đã được xác nhận",
                    "Đơn " + code + " đã được xác nhận và đang được chuẩn bị. " + OrderService.moneyLines(order) + "."};
            case SHIPPING -> new String[]{"Đơn hàng đang được giao",
                    "Đơn " + code + " đã được giao cho đơn vị vận chuyển. Nhớ quay video khi mở hàng: video mở hàng"
                            + " là bắt buộc nếu bạn cần đổi hoặc trả hàng."};
            case DELIVERED -> new String[]{"Đơn hàng đã giao thành công",
                    "Đơn " + code + " đã giao xong. Bạn có thể đánh giá sản phẩm trong trang đơn hàng."};
            case CANCELLED -> new String[]{"Đơn hàng đã bị hủy",
                    "Đơn " + code + " đã được cửa hàng hủy. Liên hệ cửa hàng nếu bạn cần hỗ trợ."};
            case PENDING -> null;
        };
        if (content != null) {
            notificationService.notify(order.getCustomer(), NotificationType.ORDER_STATUS, content[0], content[1],
                    "/orders/" + order.getId());
        }
    }

    private void createChildProductsForDeliveredOrder(Order order) {
        // gom các bé có thêm sản phẩm để mỗi bé chỉ tính lại hồ sơ kỹ năng 1 lần, dù đơn có nhiều dòng cho cùng bé
        Map<Long, ChildProfile> changedChildren = new LinkedHashMap<>();
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
            changedChildren.put(child.getId(), child);
        }
        for (ChildProfile child : changedChildren.values()) {
            // điểm trước khi thêm đồ chơi, để báo phụ huynh hồ sơ của bé vừa thay đổi thế nào
            Map<Long, Double> before = skillProfileService.storedScores(child.getId());
            SkillProfileResponse after = skillProfileService.recalculateFor(child);
            notificationService.notify(order.getCustomer(), NotificationType.SKILL_PROFILE_UPDATED,
                    "Hồ sơ kỹ năng của bé " + child.getName() + " vừa cập nhật",
                    skillChangeMessage(order, before, after),
                    "/children/" + child.getId());
        }
    }

    // "Nhờ đơn BB123: Sáng tạo +2,6, Logic +0,8." (tối đa 3 nhóm tăng nhiều nhất)
    private String skillChangeMessage(Order order, Map<Long, Double> before, SkillProfileResponse after) {
        Map<String, Double> gainBySkill = after.skillScores().stream()
                .collect(Collectors.toMap(SkillScoreResponse::skillName,
                        score -> score.score() - before.getOrDefault(score.skillId(), 0.0)));
        String gains = gainBySkill.entrySet().stream()
                .filter(entry -> entry.getValue() >= 0.05) // còn hiện được "+0,1" sau khi làm tròn
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(entry -> entry.getKey() + " +" + SkillScoreCalculator.format(entry.getValue()))
                .collect(Collectors.joining(", "));
        return gains.isEmpty()
                ? "Đồ chơi trong đơn " + order.getOrderCode() + " đã được thêm vào hồ sơ của bé."
                : "Nhờ đơn " + order.getOrderCode() + ": " + gains + ".";
    }
    private OrderSummaryResponse toSummaryResponse(Order order) {
        Customer customer = order.getCustomer();
        return new OrderSummaryResponse(order.getId(), order.getOrderCode(),
                customer.getFullName(), customer.getEmail(), order.getSubtotalOrTotal(), order.getShippingFeeOrZero(),
                order.getTotalAmount(),
                order.getStatus().name(), order.getPaymentMethod().name(), order.getCreatedAt());
    }

}
