package com.brainblocks.backend.dto.response.auth;

public record LoginResponse(String token, String role, Long userId) {
}
