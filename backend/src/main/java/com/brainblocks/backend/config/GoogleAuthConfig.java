package com.brainblocks.backend.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Đăng nhập Google: kiểm tra ID token (chữ ký theo khóa công khai của Google, issuer, hạn dùng, audience).
 * GOOGLE_CLIENT_ID là OAuth Client ID của web app, KHÁC GOOGLE_API_KEY của Gemini (GeminiConfig).
 * Để trống thì app vẫn chạy, chỉ /api/auth/google trả 503: bean null, GoogleLoginService nhận qua ObjectProvider.
 */
@Configuration
public class GoogleAuthConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        if (clientId == null || clientId.isBlank()) return null;
        // verifier tự cache khóa công khai của Google theo Cache-Control, không tải lại mỗi request
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId.trim()))
                .build();
    }
}
