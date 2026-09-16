package com.ctu.hehe.dto.response;

public record LoginResponse(String token, String role, Long userId) {
}
