package com.brainblocks.backend.controller;

import com.brainblocks.backend.dto.request.auth.ForgotPasswordRequest;
import com.brainblocks.backend.dto.request.auth.LoginRequest;
import com.brainblocks.backend.dto.request.auth.RegisterRequest;
import com.brainblocks.backend.dto.request.auth.ResetPasswordRequest;
import com.brainblocks.backend.dto.request.auth.SendVerificationCodeRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.dto.response.user.UserResponse;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.service.auth.EmailVerificationService;
import com.brainblocks.backend.service.auth.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Register successfully", authService.register(request)));
    }

    // gửi mã xác nhận tới email trước khi đăng ký (EmailVerificationService); email đã có tài khoản trả 409
    @PostMapping("/register/send-code")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @Valid @RequestBody SendVerificationCodeRequest request) {
        emailVerificationService.sendCode(request.email());
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", null));
    }

    // email chưa có tài khoản: 404; tài khoản bị khóa: 403 (PasswordResetService)
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok(ApiResponse.success("Reset link sent", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Password has been reset", null));
    }
}
