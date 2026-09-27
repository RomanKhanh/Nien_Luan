package com.brainblocks.backend.service.cart;

import com.brainblocks.backend.dto.request.cart.AddCartItemRequest;
import com.brainblocks.backend.dto.request.cart.UpdateCartItemQuantityRequest;
import com.brainblocks.backend.dto.response.cart.CartItemResponse;
import com.brainblocks.backend.dto.response.cart.CartResponse;
import com.brainblocks.backend.entity.Cart;
import com.brainblocks.backend.entity.CartItem;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        return toResponse(getOwnedCart());
    }

    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        Cart cart = getOwnedCart();

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i->i.getProduct().getId().equals(request.productId()))
                .findFirst();

        int newQuantity = existing.map(item -> item.getQuantity() + request.quantity())
                .orElse(request.quantity());

        if (newQuantity > product.getStockQuantity()) {
            throw new IllegalArgumentException("Not enough stock for: " + product.getName());
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(newQuantity);
        } else {
            cart.getItems().add(CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .build()
            );
        }
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long itemId, UpdateCartItemQuantityRequest request) {
        Cart cart = getOwnedCart();
        CartItem item = findOwnedItem(cart, itemId);

        if (request.quantity() > item.getProduct().getStockQuantity()) {
            throw new IllegalArgumentException("Not enough stock for: " + item.getProduct().getName());
        }

        item.setQuantity(request.quantity());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long itemId) {
        Cart cart = getOwnedCart();
        cart.getItems().remove(findOwnedItem(cart, itemId));
        return toResponse(cart);
    }

    @Transactional
    public void clearCart() {
        getOwnedCart().getItems().clear();
    }

    private Cart getOwnedCart() {
        return cartRepository.findWithItemsByCustomerId(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    private CartItem findOwnedItem(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(this::toItemResponse).toList();
        BigDecimal total = items.stream().map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, total);
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product p = item.getProduct();
        BigDecimal subTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return  new CartItemResponse(
            item.getId(), p.getId(), p.getName(), p.getPrice(), item.getQuantity(),subTotal
        );
    }
}
