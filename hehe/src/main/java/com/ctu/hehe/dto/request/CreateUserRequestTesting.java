package com.ctu.hehe.dto.request;

import com.ctu.hehe.enums.Role;

public record CreateUserRequestTesting(String fullName, String email, String password, Role role) {
}
