package com.brainblocks.backend.security;

import com.brainblocks.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/**
 * Token trong link đặt lại mật khẩu: JWT mang email + tokenVersion của tài khoản lúc tạo, hết hạn sau
 * app.password-reset.expiry-minutes phút. Không lưu DB: đặt lại mật khẩu (hoặc đổi mật khẩu) làm tokenVersion tăng,
 * nên link chỉ dùng được 1 lần và mọi link cũ hết hiệu lực.
 * Ký bằng khóa riêng suy ra từ jwt.secret, nên token này không dùng làm token đăng nhập được và ngược lại.
 */
@Service
public class PasswordResetTokenService {
    private static final String TOKEN_VERSION_CLAIM = "ver";
    private static final String KEY_PURPOSE = "brainblocks-password-reset";

    private final SecretKey signingKey;
    private final Duration validity;

    public PasswordResetTokenService(@Value("${jwt.secret}") String jwtSecret,
                                     @Value("${app.password-reset.expiry-minutes:30}") long expiryMinutes) {
        this.signingKey = deriveKey(Decoders.BASE64.decode(jwtSecret));
        this.validity = Duration.ofMinutes(expiryMinutes);
    }

    public Duration validity() {
        return validity;
    }

    public String generate(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(new Date(now))
                .expiration(new Date(now + validity.toMillis()))
                .signWith(signingKey)
                .compact();
    }

    /** Email và tokenVersion trong token; rỗng nếu token sai chữ ký, hỏng hoặc đã hết hạn. */
    public Optional<Claim> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
            if (claims.getSubject() == null || version == null) {
                return Optional.empty();
            }
            return Optional.of(new Claim(claims.getSubject(), version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record Claim(String email, int tokenVersion) {}

    // SHA-256(jwt.secret || mục đích): khóa khác hẳn khóa ký token đăng nhập
    private static SecretKey deriveKey(byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            digest.update(KEY_PURPOSE.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
