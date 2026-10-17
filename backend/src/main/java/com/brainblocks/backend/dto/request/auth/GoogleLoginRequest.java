package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

// idToken: "credential" (JWT) mà Google Identity Services trả cho frontend
public record GoogleLoginRequest(@NotBlank String idToken) {
}
