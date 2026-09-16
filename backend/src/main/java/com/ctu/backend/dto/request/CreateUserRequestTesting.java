package com.ctu.backend.dto.request;

import com.ctu.backend.enums.Role;

public record CreateUserRequestTesting(String fullName, String email, String password, Role role) {
}
