package com.brainblocks.backend.dto.response.location;

// hasWards = false với huyện đảo không có cấp xã: không cần chọn phường/xã
public record DistrictResponse(int code, String name, boolean hasWards) {
}
