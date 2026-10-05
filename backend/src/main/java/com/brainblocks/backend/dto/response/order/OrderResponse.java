package com.brainblocks.backend.dto.response.order;

import com.brainblocks.backend.dto.response.location.AddressResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đơn hàng cho khách và admin.
 * shippingAddress: địa chỉ đầy đủ để hiển thị ("chi tiết, xã, huyện, tỉnh"; đơn cũ là chuỗi địa chỉ cũ);
 * address: địa chỉ 3 cấp, null ở đơn cũ.
 * subtotal: tiền hàng; shippingFee: phí vận chuyển. Đơn trước khi có phí ship: shippingFee = 0,
 * shippingZone / shippingZoneLabel / parcelCount = null.
 * shippingDiscount (freeship) / discountAmount (voucher giảm giá): tiền được giảm, 0 khi không dùng voucher;
 * totalAmount = subtotal + shippingFee - shippingDiscount - discountAmount.
 * cancelledByCustomer: khách tự hủy lúc đơn còn chờ xác nhận (khi đó không gửi phản hồi về đơn được nữa).
 * paymentDeadline: hạn thanh toán của đơn MoMo chưa trả (quá hạn thì tự hủy, xem UnpaidOrderPolicy); null ở đơn khác.
 */
public record OrderResponse(Long id, String orderCode, String receiverName, String receiverPhone,
                            String shippingAddress, AddressResponse address,
                            BigDecimal subtotal, BigDecimal shippingFee, String shippingZone,
                            String shippingZoneLabel, Integer parcelCount, BigDecimal shippingDiscount,
                            BigDecimal discountAmount, BigDecimal totalAmount, String status,
                            String paymentMethod, boolean cancelledByCustomer, LocalDateTime paymentDeadline,
                            List<OrderItemResponse> items, LocalDateTime createdAt) {}
