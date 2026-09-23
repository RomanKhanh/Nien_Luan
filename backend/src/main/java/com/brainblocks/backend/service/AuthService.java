package com.brainblocks.backend.service;

import com.brainblocks.backend.dto.request.LoginRequest;
import com.brainblocks.backend.dto.request.RegisterRequest;
import com.brainblocks.backend.dto.response.LoginResponse;
import com.brainblocks.backend.dto.response.UserResponse;
import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.Cart;
import com.brainblocks.backend.entity.Customer;
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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // sơ đồ yêu cầu lưu lần đăng nhập gần nhất của admin
        userRepository.findById(userDetails.getId())
                .filter(Admin.class::isInstance)
                .map(Admin.class::cast)
                .ifPresent(admin -> admin.setLastLoginAt(LocalDateTime.now()));

        String token = jwtService.generateToken(userDetails);
        return new LoginResponse(token, userDetails.getRole().name(), userDetails.getId());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already in use");
        }

        Customer customer = Customer.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER) // đăng ký công khai luôn là CUSTOMER, không nhận role từ client
                .build();

        // mỗi khách hàng có đúng một giỏ hàng, tạo cùng lúc với tài khoản
        Cart cart = Cart.builder().customer(customer).build();
        customer.setCart(cart);

        Customer saved = userRepository.save(customer);
        return new UserResponse(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole().name());
    }
}
