package com.brainblocks.backend.service.order;

import com.brainblocks.backend.dto.request.order.CreateOrderRequest;
import com.brainblocks.backend.dto.response.order.OrderItemResponse;
import com.brainblocks.backend.dto.response.order.OrderResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderAccessGuard orderAccessGuard;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Long customerId = currentUserProvider.getCurrentUser().getId();
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found!"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty!");
        }

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .customer(customerRepository.getReferenceById(customerId))
                .receiverName(request.receiverName().trim())
                .receiverPhone(request.receiverPhone().trim())
                .shippingAddress(request.shippingAddress().trim())
                .status(OrderStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product is no longer available: " + product.getName());
            }
            if (item.getQuantity() > product.getStockQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .product(product)
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
        Order order = orderAccessGuard.getOwnedOrder(orderId);
        if (!(order.getStatus().equals(OrderStatus.PENDING) || order.getStatus().equals(OrderStatus.CONFIRMED))) {
            throw new IllegalArgumentException("Can't cancel this order because it is already " + order.getStatus());
        }
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return toOrderResponse(order);
    }


    private String generateOrderCode() {
        return "ORD" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                        ))
                .toList();
        return new OrderResponse(order.getId(), order.getOrderCode(), order.getReceiverName(),
                order.getReceiverPhone(), order.getShippingAddress(), order.getTotalAmount(),
                order.getStatus().name(), order.getPaymentMethod().name(), items, order.getCreatedAt());
    }
}
