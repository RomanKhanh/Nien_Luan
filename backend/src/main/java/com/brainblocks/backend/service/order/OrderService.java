package com.brainblocks.backend.service.order;

import com.brainblocks.backend.dto.request.order.CreateOrderRequest;
import com.brainblocks.backend.dto.request.order.OrderItemChildRequest;
import com.brainblocks.backend.dto.response.order.OrderItemResponse;
import com.brainblocks.backend.dto.response.order.OrderResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderAccessGuard orderAccessGuard;
    private final ChildProfileAccessGuard childProfileAccessGuard;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        Long customerId = currentUserProvider.getCurrentUser().getId();
        // khóa giỏ trước khi đọc các dòng giỏ, để 2 lần đặt hàng song song không cùng dùng một giỏ
        cartRepository.findForUpdateByCustomerId(customerId);
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found!"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty!");
        }

        Map<Long, ChildProfile> childByProductId = resolveChildAssignments(request.childAssignments(), cart);

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .customer(customerRepository.getReferenceById(customerId))
                .receiverName(request.receiverName().trim())
                .receiverPhone(request.receiverPhone().trim())
                .shippingAddress(request.shippingAddress().trim())
                .status(OrderStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .build();

        // trừ kho theo thứ tự id sản phẩm cố định để 2 đơn song song không khóa chéo nhau (deadlock)
        List<CartItem> cartItems = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product is no longer available: " + product.getName());
            }
            // kiểm tra và trừ trong cùng một câu update; lỗi ở dòng sau thì cả transaction rollback
            if (productRepository.decreaseStock(product.getId(), item.getQuantity()) == 0) {
                throw new IllegalArgumentException("Insufficient stock for: " + product.getName());
            }

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
                    .childProfile(childByProductId.get(product.getId()))
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .build());

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        cart.getItems().clear();

        return toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        return orderRepository
                .findAllWithItemsByCustomerId(currentUserProvider.getCurrentUserId()).stream()
                .map(this::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderAccessGuard.getOwnedOrder(orderId);
        return toOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        // khóa đơn trước khi đọc trạng thái (xem OrderRepository.findForUpdateById); đơn không tồn tại thì guard trả 404
        orderRepository.findForUpdateById(orderId);
        Order order = orderAccessGuard.getOwnedOrder(orderId);
        if (!(order.getStatus().equals(OrderStatus.PENDING) || order.getStatus().equals(OrderStatus.CONFIRMED))) {
            throw new IllegalArgumentException("Can't cancel this order because it is already " + order.getStatus());
        }
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        return toOrderResponse(order);
    }

    // hoàn kho cho mọi dòng của đơn bị hủy; dùng chung cho khách tự hủy và admin hủy
    void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            productRepository.increaseStock(item.getProduct().getId(), item.getQuantity());
        }
    }

    /**
     * Chọn bé cho từng sản phẩm ở bước checkout (quan hệ "dành cho" OrderItem - ChildProfile trên sơ đồ lớp).
     * Mỗi sản phẩm trong giỏ gắn tối đa một bé; bé phải thuộc khách đang đặt hàng (kiểm tra qua ChildProfileAccessGuard).
     */
    private Map<Long, ChildProfile> resolveChildAssignments(List<OrderItemChildRequest> assignments, Cart cart) {
        if (assignments == null || assignments.isEmpty()) {
            return Map.of();
        }
        Set<Long> productIdsInCart = cart.getItems().stream()
                .map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());

        Map<Long, ChildProfile> childByProductId = new HashMap<>();
        for (OrderItemChildRequest assignment : assignments) {
            if (!productIdsInCart.contains(assignment.productId())) {
                throw new IllegalArgumentException("Product " + assignment.productId() + " is not in the cart");
            }
            if (childByProductId.containsKey(assignment.productId())) {
                throw new IllegalArgumentException("Product " + assignment.productId() + " is assigned to more than one child");
            }
            childByProductId.put(assignment.productId(),
                    childProfileAccessGuard.getOwnedChildProfile(assignment.childProfileId()));
        }
        return childByProductId;
    }

    private String generateOrderCode() {
        return "ORD" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> {
                    ChildProfile child = i.getChildProfile();
                    return new OrderItemResponse(
                            i.getId(),
                            i.getProduct().getId(),
                            i.getProduct().getName(),
                            i.getQuantity(),
                            i.getUnitPrice(),
                            i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
                            child == null ? null : child.getId(),
                            child == null ? null : child.getName());
                })
                .toList();
        return new OrderResponse(order.getId(), order.getOrderCode(), order.getReceiverName(),
                order.getReceiverPhone(), order.getShippingAddress(), order.getTotalAmount(),
                order.getStatus().name(), order.getPaymentMethod().name(), items, order.getCreatedAt());
    }
}
