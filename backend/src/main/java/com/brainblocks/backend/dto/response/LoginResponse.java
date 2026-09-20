package com.brainblocks.backend.dto.response;

public record LoginResponse(String token, String role, Long userId) {
}
