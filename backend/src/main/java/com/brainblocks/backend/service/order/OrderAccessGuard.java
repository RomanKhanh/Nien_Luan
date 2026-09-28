package com.brainblocks.backend.service.order;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAccessGuard {
    private final OrderRepository orderRepository;
    private final CurrentUserProvider currentUserProvider;

    public Order getOwnedOrder(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // đơn của người khác trả 404 giống đơn không tồn tại, để không lộ id nào là đơn có thật
        if (!order.getCustomer().getId().equals(currentUserProvider.getCurrentUserId())) {
            throw new ResourceNotFoundException("Order not found");
        }
        return order;
    }
}
