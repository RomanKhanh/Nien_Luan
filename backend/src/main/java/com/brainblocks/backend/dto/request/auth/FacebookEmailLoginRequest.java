package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.*;

public record FacebookEmailLoginRequest(@NotBlank @Size(max = 8192) String accessToken,
                                       @NotBlank @Email @Size(max = 255) String email,
                                       @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
