package com.brainblocks.backend.dto.response.location;

/**
 * Địa chỉ giao hàng theo 3 cấp. ward null khi huyện không có cấp xã.
 * fullAddress = "chi tiết, xã, huyện, tỉnh" để hiển thị.
 */
public record AddressResponse(
        Integer provinceCode,
        String provinceName,
        Integer districtCode,
        String districtName,
        Integer wardCode,
        String wardName,
        String addressDetail,
        String fullAddress
) {
}
