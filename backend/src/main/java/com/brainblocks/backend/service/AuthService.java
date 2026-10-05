package com.brainblocks.backend.service;

import com.brainblocks.backend.dto.request.auth.LoginRequest;
import com.brainblocks.backend.dto.request.auth.RegisterRequest;
import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.dto.response.user.UserResponse;
import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.Cart;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.exception.DuplicateEmailException;
import com.brainblocks.backend.repository.CartRepository;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.CustomUserDetails;
import com.brainblocks.backend.security.JwtService;
import com.brainblocks.backend.util.EmailUtils;
import com.brainblocks.backend.service.auth.EmailVerificationService;
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
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(EmailUtils.normalize(request.email()), request.password())
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
        String email = EmailUtils.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already in use");
        }
        // email phải là hộp thư thật mà người đăng ký mở được: kiểm tra mã đã gửi tới đó
        emailVerificationService.verify(email, request.verificationCode());

        Customer customer = Customer.builder()
                .fullName(request.fullName())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .build(); // role CUSTOMER suy ra từ lớp Customer, không nhận role từ client

        Customer saved = userRepository.save(customer);

        // mỗi khách hàng có đúng một giỏ hàng, tạo cùng lúc với tài khoản (cùng transaction)
        cartRepository.save(Cart.builder().customer(saved).build());
        return new UserResponse(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getRole().name());
    }
}
