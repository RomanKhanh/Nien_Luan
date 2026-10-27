package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FacebookLoginRequest(@NotBlank @Size(max = 8192) String accessToken) {}
