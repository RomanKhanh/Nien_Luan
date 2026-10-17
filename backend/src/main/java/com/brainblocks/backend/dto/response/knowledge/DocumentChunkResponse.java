package com.brainblocks.backend.dto.response.knowledge;

// Chỉ trả cờ embedded; không gửi mảng vector lớn ra frontend.
public record DocumentChunkResponse(Long id, int chunkIndex, String content, boolean embedded) {}
