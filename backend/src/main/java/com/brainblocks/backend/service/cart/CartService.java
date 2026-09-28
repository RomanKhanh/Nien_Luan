package com.brainblocks.backend.service.cart;

import com.brainblocks.backend.dto.request.cart.AddCartItemRequest;
import com.brainblocks.backend.dto.request.cart.UpdateCartItemQuantityRequest;
import com.brainblocks.backend.dto.response.cart.CartItemResponse;
import com.brainblocks.backend.dto.response.cart.CartResponse;
import com.brainblocks.backend.entity.Cart;
import com.brainblocks.backend.entity.CartItem;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CartItemRepository;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductRepository.ProductThumbnail;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
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
                .filter(Product::isActive) // sản phẩm đã ẩn coi như không tồn tại với phía khách hàng
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i->i.getProduct().getId().equals(request.productId()))
                .findFirst();

        // cộng bằng long để không tràn int, rồi mới so với giới hạn mỗi món và tồn kho
        long newQuantity = existing.map(item -> (long) item.getQuantity() + request.quantity())
                .orElse((long) request.quantity());

        if (newQuantity > CartItem.MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity per product cannot exceed " + CartItem.MAX_QUANTITY);
        }
        if (newQuantity > product.getStockQuantity()) {
            throw new IllegalArgumentException("Not enough stock for: " + product.getName());
        }

        if (existing.isPresent()) {
            existing.get().setQuantity((int) newQuantity);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.quantity())
                    .build();
            // save ngay (IDENTITY nên INSERT luôn) để response có id; nếu chỉ add vào list thì
            // cascade mới lưu lúc commit, sau khi response đã dựng xong với id = null
            cart.getItems().add(cartItemRepository.save(item));
        }
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(Long itemId, UpdateCartItemQuantityRequest request) {
        Cart cart = getOwnedCart();
        CartItem item = findOwnedItem(cart, itemId);

        if (!item.getProduct().isActive()) {
            throw new IllegalArgumentException("Product is no longer available: " + item.getProduct().getName());
        }
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
        // ảnh đại diện của cả giỏ lấy trong 1 câu
        List<Long> productIds = cart.getItems().stream().map(item -> item.getProduct().getId()).toList();
        Map<Long, String> thumbnailByProductId = productIds.isEmpty() ? Map.of() : productRepository
                .findThumbnails(productIds).stream()
                .collect(Collectors.toMap(ProductThumbnail::getProductId, ProductThumbnail::getUrl, (a, b) -> a));

        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> toItemResponse(item, thumbnailByProductId.get(item.getProduct().getId())))
                .toList();
        BigDecimal total = items.stream().map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), items, total);
    }

    private CartItemResponse toItemResponse(CartItem item, String thumbnailUrl) {
        Product p = item.getProduct();
        BigDecimal subTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(), p.getId(), p.getName(), thumbnailUrl, p.getPrice(), item.getQuantity(), subTotal,
                p.getStockQuantity(), p.isActive()
        );
    }
}
