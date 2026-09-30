package com.brainblocks.backend.dto.response;

// bản đầy đủ của SkillResponse, dùng cho danh sách nhóm kỹ năng và trang quản trị
public record SkillDetailResponse(Long id, String code, String name, String description) {
}
