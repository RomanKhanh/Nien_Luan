package com.brainblocks.backend.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        // cho phép để trống; nếu nhập thì chỉ gồm số, có thể bắt đầu bằng +
        @Pattern(regexp = "^$|^\\+?[0-9]{9,14}$", message = "must be a valid phone number") String phone,
        // địa chỉ mặc định 3 cấp (chỉ khách hàng): gửi đủ mã tỉnh, huyện, xã (trừ huyện không có cấp xã) và
        // địa chỉ chi tiết; để trống cả 4 thì xóa địa chỉ mặc định
        Integer defaultProvinceCode,
        Integer defaultDistrictCode,
        Integer defaultWardCode,
        @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự") String defaultAddressDetail
) {
}
