package com.brainblocks.backend.service.order;

import com.brainblocks.backend.config.LegacyOrderMigration;
import com.brainblocks.backend.dto.request.order.CreateOrderRequest;
import com.brainblocks.backend.dto.request.order.OrderItemChildRequest;
import com.brainblocks.backend.dto.response.order.OrderItemResponse;
import com.brainblocks.backend.dto.response.location.AddressResponse;
import com.brainblocks.backend.dto.response.order.OrderResponse;
import com.brainblocks.backend.entity.*;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.PaymentMethod;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.exception.ShippingFeeChangedException;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.location.LocationDirectory;
import com.brainblocks.backend.service.location.LocationDirectory.ResolvedAddress;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.shipping.ShippingQuote;
import com.brainblocks.backend.service.shipping.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final NotificationService notificationService;
    private final LocationDirectory locations;
    private final ShippingQuoteService shippingQuoteService;

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
        ResolvedAddress address = locations.resolve(request.provinceCode(), request.districtCode(), request.wardCode());

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .customer(customerRepository.getReferenceById(customerId))
                .receiverName(request.receiverName().trim())
                .receiverPhone(request.receiverPhone().trim())
                // lưu cả tên tại thời điểm đặt; shippingAddress (địa chỉ tự do kiểu cũ) để trống
                .provinceCode(address.province().code())
                .provinceName(address.province().name())
                .districtCode(address.district().code())
                .districtName(address.district().name())
                .wardCode(address.ward() == null ? null : address.ward().code())
                .wardName(address.ward() == null ? null : address.ward().name())
                .addressDetail(request.addressDetail().trim())
                .status(OrderStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .build();

        // trừ kho theo thứ tự id sản phẩm cố định để 2 đơn song song không khóa chéo nhau (deadlock)
        List<CartItem> cartItems = cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        BigDecimal subtotal = BigDecimal.ZERO;
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

            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // Phí vận chuyển server tự tính lại từ đúng các món của đơn + tỉnh nhận (cùng hàm với /api/shipping/quote),
        // không nhận từ client. Lệch với phí khách đã thấy thì trả 409; exception rollback cả phần trừ kho ở trên.
        ShippingQuote shipping = shippingQuoteService.quote(address.province(), cartItems);
        BigDecimal shippingFee = shipping.totalFee();
        if (request.expectedShippingFee() != null && request.expectedShippingFee().compareTo(shippingFee) != 0) {
            throw new ShippingFeeChangedException("Phí vận chuyển đã thay đổi thành " + formatMoney(shippingFee)
                    + " (tổng thanh toán " + formatMoney(subtotal.add(shippingFee)) + ") do giỏ hàng vừa thay đổi. "
                    + "Vui lòng kiểm tra lại và bấm đặt hàng để xác nhận.", shippingFee, subtotal);
        }
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setShippingZone(shipping.zone());
        order.setParcelCount(shipping.parcelCount());
        order.setTotalAmount(subtotal.add(shippingFee));

        Order saved = orderRepository.save(order);
        cart.getItems().clear();

        notificationService.notifyAdmins(NotificationType.NEW_ORDER, "Đơn hàng mới " + saved.getOrderCode(),
                saved.getCustomer().getFullName() + " vừa đặt " + saved.getItems().size() + " sản phẩm. "
                        + moneyLines(saved) + ".",
                "/admin/orders");
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
        // khách chỉ tự hủy được khi đơn còn chờ xác nhận; đã xác nhận thì phải gửi yêu cầu hủy (ComplaintType.CANCEL)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Can't cancel this order because it is already " + order.getStatus());
        }
        // đơn chờ xác nhận là đơn chưa thanh toán (MoMo trả thành công thì đơn tự chuyển sang Đã xác nhận),
        // nên khách hủy là trả hàng về giỏ để sửa lại rồi đặt tiếp, với mọi hình thức thanh toán.
        // Làm trước restoreStock: khóa giỏ rồi mới khóa dòng sản phẩm, cùng thứ tự với createOrder để không deadlock
        returnItemsToCart(order);
        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledByCustomer(true);
        notificationService.notifyAdmins(NotificationType.ORDER_CANCELLED_BY_CUSTOMER,
                LegacyOrderMigration.SELF_CANCEL_TITLE + order.getOrderCode(),
                order.getCustomer().getFullName() + " đã tự hủy đơn, hàng đã được hoàn về kho.",
                "/admin/orders");
        return toOrderResponse(order);
    }

    // Cộng dồn từng dòng đơn vào giỏ của khách (giỏ mỗi sản phẩm một dòng). Bỏ qua sản phẩm đã ẩn và
    // giới hạn MAX_QUANTITY mỗi món như CartService.addItem. Không so với tồn kho ở đây: increaseStock vừa
    // cộng thẳng trong DB nên Product trong bộ nhớ có thể còn số cũ; lúc đặt lại createOrder vẫn kiểm tra kho.
    private void returnItemsToCart(Order order) {
        Long customerId = order.getCustomer().getId();
        // khóa giỏ trước khi đọc, giống createOrder
        cartRepository.findForUpdateByCustomerId(customerId);
        Cart cart = cartRepository.findWithItemsByCustomerId(customerId).orElse(null);
        if (cart == null) {
            return;
        }
        for (OrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            if (!product.isActive()) {
                continue;
            }
            CartItem cartItem = cart.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(product.getId()))
                    .findFirst()
                    .orElse(null);
            int current = cartItem == null ? 0 : cartItem.getQuantity();
            int quantity = Math.min(current + orderItem.getQuantity(), CartItem.MAX_QUANTITY);
            if (quantity <= current) {
                continue;
            }
            if (cartItem == null) {
                cart.getItems().add(CartItem.builder().cart(cart).product(product).quantity(quantity).build());
            } else {
                cartItem.setQuantity(quantity);
            }
        }
    }

    // 459000 -> "459.000₫"
    private static String formatMoney(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "₫";
    }

    // "Tiền hàng 459.000₫, phí vận chuyển 26.000₫, tổng 485.000₫" cho thông báo đơn hàng
    public static String moneyLines(Order order) {
        return "Tiền hàng " + formatMoney(order.getSubtotalOrTotal()) + ", phí vận chuyển "
                + formatMoney(order.getShippingFeeOrZero()) + ", tổng " + formatMoney(order.getTotalAmount());
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
                order.getReceiverPhone(), order.fullAddress(), toAddressResponse(order), order.getSubtotalOrTotal(),
                order.getShippingFeeOrZero(), order.getShippingZone() == null ? null : order.getShippingZone().name(),
                order.getShippingZone() == null ? null : order.getShippingZone().label(), order.getParcelCount(),
                order.getTotalAmount(),
                order.getStatus().name(), order.getPaymentMethod().name(), order.isCancelledByCustomer(), items,
                order.getCreatedAt());
    }

    private static AddressResponse toAddressResponse(Order order) {
        if (order.getProvinceCode() == null) {
            return null;
        }
        return new AddressResponse(order.getProvinceCode(), order.getProvinceName(), order.getDistrictCode(),
                order.getDistrictName(), order.getWardCode(), order.getWardName(), order.getAddressDetail(),
                order.fullAddress());
    }
}
