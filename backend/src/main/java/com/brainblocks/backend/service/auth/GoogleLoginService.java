package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.GoogleSignInException;
import com.brainblocks.backend.exception.GoogleSignInUnavailableException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.util.EmailUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Đăng nhập bằng Google: frontend lấy ID token từ Google Identity Services, server kiểm tra token rồi phát JWT
 * giống /api/auth/login.
 * <ul>
 *   <li>Token phải đúng chữ ký Google, còn hạn, audience = GOOGLE_CLIENT_ID (GoogleAuthConfig), email_verified = true.</li>
 *   <li>Tìm tài khoản theo google subject trước, sau đó theo email: email đã có tài khoản thì gắn subject vào (liên kết),
 *       chưa có thì tạo khách hàng mới như đăng ký thường (giỏ hàng + voucher chào mừng).</li>
 *   <li>Tài khoản tạo bằng Google không có mật khẩu dùng được: lưu băm của chuỗi ngẫu nhiên, khách muốn đăng nhập
 *       bằng mật khẩu thì đặt qua "Quên mật khẩu".</li>
 *   <li>Tài khoản bị khóa: 403 như đăng nhập thường, không liên kết / không phát token.</li>
 * </ul>
 */
@Slf4j
@Service
public class GoogleLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int FULL_NAME_MAX = 100;

    private final ObjectProvider<GoogleIdTokenVerifier> verifierProvider;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public GoogleLoginService(ObjectProvider<GoogleIdTokenVerifier> verifierProvider, UserRepository userRepository,
                              AuthService authService, PasswordEncoder passwordEncoder) {
        this.verifierProvider = verifierProvider;
        this.userRepository = userRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(String idToken) {
        GoogleIdToken.Payload payload = verify(idToken);
        if (!Boolean.TRUE.equals(payload.getEmailVerified()) || payload.getEmail() == null) {
            throw new GoogleSignInException("Google email is not verified");
        }
        String subject = payload.getSubject();
        String email = EmailUtils.normalize(payload.getEmail());

        User user = userRepository.findByGoogleSubject(subject)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);
        if (user == null) {
            user = authService.createCustomer(Customer.builder()
                    .fullName(fullName(payload, email))
                    .email(email)
                    .password(passwordEncoder.encode(randomSecret()))
                    .googleSubject(subject)
                    .build());
            log.info("Created customer {} from Google sign-in", user.getId());
        } else {
            if (!user.isEnabled()) {
                throw new DisabledException("Account is locked");
            }
            if (user.getGoogleSubject() == null) {
                user.setGoogleSubject(subject);
                log.info("Linked Google account to user {}", user.getId());
            } else if (!user.getGoogleSubject().equals(subject)) {
                throw new GoogleSignInException("This email is linked to a different Google account");
            }
        }
        return authService.issueToken(user);
    }

    // token hỏng / sai chữ ký / sai audience / hết hạn: 401. Không tải được khóa công khai của Google: 503
    private GoogleIdToken.Payload verify(String idToken) {
        GoogleIdTokenVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new GoogleSignInUnavailableException();
        }
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), idToken);
        } catch (IOException | IllegalArgumentException e) {
            throw invalidToken();
        }
        try {
            if (!verifier.verify(token)) {
                throw invalidToken();
            }
        } catch (GeneralSecurityException e) {
            throw invalidToken();
        } catch (IOException e) {
            throw new GoogleSignInUnavailableException(e);
        }
        return token.getPayload();
    }

    private static GoogleSignInException invalidToken() {
        return new GoogleSignInException("Invalid Google credential");
    }

    // tên hiển thị trong token, không có thì lấy phần trước @ của email
    private static String fullName(GoogleIdToken.Payload payload, String email) {
        Object name = payload.get("name");
        String value = name instanceof String s && !s.isBlank() ? s.trim() : email.substring(0, email.indexOf('@'));
        return value.length() > FULL_NAME_MAX ? value.substring(0, FULL_NAME_MAX) : value;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
