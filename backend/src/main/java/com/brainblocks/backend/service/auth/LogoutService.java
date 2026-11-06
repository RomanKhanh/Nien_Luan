package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.entity.RevokedToken;
import com.brainblocks.backend.repository.RevokedTokenRepository;
import com.brainblocks.backend.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;

    public void logout(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return;
        }

        String jti;
        Date expiration;
        try {
            String token = bearerToken.substring(7);
            // Đọc claims đồng thời xác minh chữ ký và thời hạn bằng parser của JwtService.
            jti = jwtService.extractJti(token);
            expiration = jwtService.extractExpiration(token);
        } catch (JwtException | IllegalArgumentException e) {
            // Đăng xuất thiếu token, token rác hoặc hết hạn vẫn thành công.
            return;
        }

        // Token cũ không có jti không thể thu hồi riêng và sẽ tự hết hạn.
        if (jti == null || jti.isBlank() || expiration == null || !expiration.after(new Date())) {
            return;
        }
        if (!revokedTokenRepository.existsById(jti)) {
            revokedTokenRepository.save(RevokedToken.builder()
                    .jti(jti)
                    .expiresAt(LocalDateTime.ofInstant(expiration.toInstant(), ZoneOffset.UTC))
                    .build());
        }
    }

    // Chỉ dọn token đã hết hạn, dùng UTC giống thời điểm lưu khi đăng xuất.
    @Scheduled(initialDelayString = "${app.auth.revoked-token-purge-ms:3600000}",
            fixedDelayString = "${app.auth.revoked-token-purge-ms:3600000}")
    @Transactional
    public void purgeExpiredTokens() {
        revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
    }
}
