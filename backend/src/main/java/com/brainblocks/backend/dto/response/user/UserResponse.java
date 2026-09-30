package com.brainblocks.backend.dto.response.user;

public record UserResponse(Long id, String fullName, String email, String role) {
}