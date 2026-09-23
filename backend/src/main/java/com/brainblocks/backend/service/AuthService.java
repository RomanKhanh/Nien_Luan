package com.brainblocks.backend.service;

import com.brainblocks.backend.dto.request.LoginRequest;
import com.brainblocks.backend.dto.request.RegisterRequest;
import com.brainblocks.backend.dto.response.LoginResponse;
import com.brainblocks.backend.dto.response.UserResponse;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.enums.Role;
import com.brainblocks.backend.exception.DuplicateEmailException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.CustomUserDetails;
import com.brainblocks.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        return new LoginResponse(token, userDetails.getRole().name(), userDetails.getId());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already in use");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER) // đăng ký công khai luôn là USER, không nhận role từ client
                .build();

        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole().name());
    }
}
