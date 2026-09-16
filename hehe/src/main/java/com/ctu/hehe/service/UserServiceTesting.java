package com.ctu.hehe.service;

import com.ctu.hehe.dto.request.CreateUserRequestTesting;
import com.ctu.hehe.entity.User;
import com.ctu.hehe.enums.Role;
import com.ctu.hehe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceTesting {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(CreateUserRequestTesting request) {
        User user = User.builder()
                .email(request.email())
                .fullName(request.fullName())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build();

        User saved = userRepository.save(user);
        return saved;
    }
}
