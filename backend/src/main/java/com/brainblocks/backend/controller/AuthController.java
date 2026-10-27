package com.brainblocks.backend.controller;

import com.brainblocks.backend.dto.request.auth.ForgotPasswordRequest;
import com.brainblocks.backend.dto.request.auth.GoogleLoginRequest;
import com.brainblocks.backend.dto.request.auth.FacebookLoginRequest;
import com.brainblocks.backend.dto.request.auth.FacebookEmailCodeRequest;
import com.brainblocks.backend.dto.request.auth.FacebookEmailLoginRequest;
import com.brainblocks.backend.service.auth.FacebookEmailLoginService;
import com.brainblocks.backend.service.auth.FacebookLoginService;
import com.brainblocks.backend.dto.request.auth.LoginRequest;
import com.brainblocks.backend.dto.request.auth.RegisterRequest;
import com.brainblocks.backend.dto.request.auth.ResetPasswordRequest;
import com.brainblocks.backend.dto.request.auth.SendLoginOtpRequest;
import com.brainblocks.backend.dto.request.auth.SendVerificationCodeRequest;
import com.brainblocks.backend.dto.request.auth.VerifyLoginOtpRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.dto.response.user.UserResponse;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.service.auth.EmailVerificationService;
import com.brainblocks.backend.service.auth.GoogleLoginService;
import com.brainblocks.backend.service.auth.LoginOtpService;
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
    private final GoogleLoginService googleLoginService;
    private final FacebookLoginService facebookLoginService;
    private final FacebookEmailLoginService facebookEmailLoginService;
    private final LoginOtpService loginOtpService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    // ID token từ Google Identity Services; email chưa có tài khoản thì tạo khách hàng mới (GoogleLoginService)
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(googleLoginService.login(request.idToken())));
    }

    // Token Facebook được xác minh ở server trước khi phát JWT nội bộ.
    @PostMapping("/facebook")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithFacebook(@Valid @RequestBody FacebookLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(facebookLoginService.login(request.accessToken())));
    }

    @PostMapping("/facebook/email/send-code")
    public ResponseEntity<ApiResponse<Void>> sendFacebookEmailCode(@Valid @RequestBody FacebookEmailCodeRequest request) {
        facebookEmailLoginService.sendCode(request.accessToken(), request.email());
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", null));
    }

    @PostMapping("/facebook/email/complete")
    public ResponseEntity<ApiResponse<LoginResponse>> completeFacebookEmail(@Valid @RequestBody FacebookEmailLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(facebookEmailLoginService.complete(request.accessToken(), request.email(), request.code())));
    }

    // đăng nhập bằng mã qua email (LoginOtpService): luôn trả cùng một câu, không lộ email nào đã có tài khoản
    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendLoginOtp(@Valid @RequestBody SendLoginOtpRequest request) {
        loginOtpService.sendCode(request.email());
        return ResponseEntity.ok(ApiResponse.success("If the email is registered, a login code has been sent", null));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyLoginOtp(@Valid @RequestBody VerifyLoginOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(loginOtpService.verify(request.email(), request.code())));
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
