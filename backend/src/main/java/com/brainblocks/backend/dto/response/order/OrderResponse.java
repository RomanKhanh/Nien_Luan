package com.brainblocks.backend.dto.response.order;

import com.brainblocks.backend.dto.response.location.AddressResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đơn hàng cho khách và admin.
 * shippingAddress: địa chỉ đầy đủ để hiển thị ("chi tiết, xã, huyện, tỉnh"; đơn cũ là chuỗi địa chỉ cũ);
 * address: địa chỉ 3 cấp, null ở đơn cũ.
 * totalAmount = subtotal (tiền hàng) + shippingFee (phí vận chuyển). Đơn trước khi có phí ship: shippingFee = 0,
 * shippingZone / shippingZoneLabel / parcelCount = null.
 * cancelledByCustomer: khách tự hủy lúc đơn còn chờ xác nhận (khi đó không gửi phản hồi về đơn được nữa).
 */
public record OrderResponse(Long id, String orderCode, String receiverName, String receiverPhone,
                            String shippingAddress, AddressResponse address,
                            BigDecimal subtotal, BigDecimal shippingFee, String shippingZone,
                            String shippingZoneLabel, Integer parcelCount, BigDecimal totalAmount, String status,
                            String paymentMethod, boolean cancelledByCustomer, List<OrderItemResponse> items,
                            LocalDateTime createdAt) {}
