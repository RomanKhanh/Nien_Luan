package com.brainblocks.backend.service.order;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAccessGuard {
    private final OrderRepository orderRepository;
    private final CurrentUserProvider currentUserProvider;

    public Order getOwnedOrder(Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getCustomer().getId().equals(currentUserProvider.getCurrentUser().getId())) {
            throw new AccessDeniedException("Order does not belong to current user");
        }
        return order;
    }
}
