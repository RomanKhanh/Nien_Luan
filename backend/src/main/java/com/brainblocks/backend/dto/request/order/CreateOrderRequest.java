package com.brainblocks.backend.dto.request.order;

import com.brainblocks.backend.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String receiverName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,14}$") String receiverPhone,
        // địa chỉ 3 cấp theo GET /api/locations/...: phải tồn tại và đúng quan hệ cha-con (kiểm tra ở OrderService).
        // wardCode bắt buộc, trừ huyện đảo không có cấp xã (DistrictResponse.hasWards = false)
        @NotNull(message = "Vui lòng chọn tỉnh/thành") Integer provinceCode,
        @NotNull(message = "Vui lòng chọn quận/huyện") Integer districtCode,
        Integer wardCode,
        @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
        @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
        String addressDetail,
        @NotNull PaymentMethod paymentMethod,
        // phí vận chuyển khách đã thấy lúc báo giá (không bắt buộc). Server luôn tự tính lại; gửi kèm mà khác
        // số server tính thì trả 409 kèm phí mới để khách xác nhận, không dùng số này để tính tiền
        @PositiveOrZero BigDecimal expectedShippingFee,
        // chọn bé cho từng sản phẩm trong giỏ ở bước checkout; bỏ trống = không gắn bé nào
        List<@NotNull @Valid OrderItemChildRequest> childAssignments
) {}
