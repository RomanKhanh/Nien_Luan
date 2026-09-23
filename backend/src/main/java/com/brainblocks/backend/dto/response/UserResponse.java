package com.brainblocks.backend.dto.response;

public record UserResponse(Long id, String fullName, String email, String role) {
}