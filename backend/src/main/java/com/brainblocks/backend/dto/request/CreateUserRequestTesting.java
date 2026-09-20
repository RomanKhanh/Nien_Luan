package com.brainblocks.backend.dto.request;

import com.brainblocks.backend.enums.Role;

public record CreateUserRequestTesting(String fullName, String email, String password, Role role) {
}
