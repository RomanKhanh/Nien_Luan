package com.brainblocks.backend.dto.response.location;

// tỉnh/thành kèm miền giao hàng (MIEN_NAM / MIEN_TRUNG / MIEN_BAC)
public record ProvinceResponse(int code, String name, String region, String regionLabel) {
}
