package com.brainblocks.backend.service;

import com.brainblocks.backend.dto.request.CreateUserRequestTesting;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.enums.Role;
import com.brainblocks.backend.repository.UserRepository;
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
