package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.security.PasswordResetTokenService;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.util.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quên mật khẩu: gửi email chứa link đặt lại (token ở PasswordResetTokenService), rồi đặt mật khẩu mới qua link đó.
 * <ul>
 *   <li>Email chưa có tài khoản báo 404, tài khoản bị khóa báo 403. Trang đăng ký vốn đã báo email trùng, nên báo rõ
 *       ở đây không lộ thêm gì. Mail gửi ở luồng riêng, request không chờ SMTP.</li>
 *   <li>Mỗi email chỉ được yêu cầu lại sau app.password-reset.cooldown-seconds giây, để không bị dùng spam hộp thư.</li>
 *   <li>Đặt lại xong: tokenVersion tăng, link vừa dùng, các link khác và mọi phiên đăng nhập cũ đều hết hiệu lực.</li>
 * </ul>
 */
@Slf4j
@Service
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final String frontendUrl;
    private final Duration cooldown;
    // email -> lần yêu cầu gần nhất (chỉ giữ trong bộ nhớ, đủ cho một máy chủ)
    private final Map<String, Instant> lastRequestAt = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenService tokenService,
                                PasswordEncoder passwordEncoder, MailService mailService,
                                @Value("${app.frontend-url}") String frontendUrl,
                                @Value("${app.password-reset.cooldown-seconds:60}") long cooldownSeconds) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    @Transactional(readOnly = true)
    public void requestReset(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!user.isEnabled()) {
            throw new DisabledException("Account is locked");
        }
        mailService.ensureCanSend();
        Instant now = Instant.now();
        Instant previous = lastRequestAt.get(email);
        if (previous != null && previous.plus(cooldown).isAfter(now)) {
            throw new IllegalArgumentException("Please wait before requesting a new reset link");
        }
        lastRequestAt.put(email, now);
        lastRequestAt.values().removeIf(at -> at.plus(cooldown).isBefore(now));
        sendResetMail(user);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetTokenService.Claim claim = tokenService.verify(token)
                .orElseThrow(PasswordResetService::invalidLink);
        // băm trước khi khóa (bcrypt chậm) để giữ khóa dòng users thật ngắn
        String encoded = passwordEncoder.encode(newPassword);
        // khóa dòng users trước khi đọc: link dùng song song 2 lần thì lần sau chờ lần trước xong, thấy tokenVersion
        // đã tăng và bị từ chối, nên mỗi link chỉ đặt được mật khẩu 1 lần
        Long userId = userRepository.lockIdByEmail(claim.email()).orElseThrow(PasswordResetService::invalidLink);
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .filter(u -> u.getTokenVersion() == claim.tokenVersion())
                .orElseThrow(PasswordResetService::invalidLink);
        user.setPassword(encoded);
        // giống UserService.changeMyPassword: JWT đăng nhập cũ và các link đặt lại cũ hết hiệu lực
        user.setTokenVersion(user.getTokenVersion() + 1);
        log.info("Password reset for user {}", user.getId());
    }

    private static IllegalArgumentException invalidLink() {
        return new IllegalArgumentException("Password reset link is invalid or has expired");
    }

    private void sendResetMail(User user) {
        String link = UriComponentsBuilder.fromUriString(frontendUrl).path("/reset-password")
                .queryParam("token", tokenService.generate(user)).build().toUriString();
        long minutes = tokenService.validity().toMinutes();
        String name = user.getFullName();
        String text = "Chào " + name + ",\n\n"
                + "BrainBlocks nhận được yêu cầu đặt lại mật khẩu cho tài khoản " + user.getEmail() + ".\n"
                + "Mở link sau để đặt mật khẩu mới (hết hạn sau " + minutes + " phút, chỉ dùng được 1 lần):\n"
                + link + "\n\n"
                + "Nếu bạn không yêu cầu, hãy bỏ qua email này, mật khẩu của bạn vẫn giữ nguyên.\n\n"
                + "BrainBlocks";
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1f2433">
                  <h2 style="color:#5b3df5;margin:0 0 16px">BrainBlocks</h2>
                  <p>Chào <b>%s</b>,</p>
                  <p>BrainBlocks nhận được yêu cầu đặt lại mật khẩu cho tài khoản <b>%s</b>.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#5b3df5;color:#ffffff;text-decoration:none;padding:12px 24px;
                       border-radius:999px;font-weight:bold;display:inline-block">Đặt mật khẩu mới</a>
                  </p>
                  <p style="color:#5b6070;font-size:14px">Link hết hạn sau %d phút và chỉ dùng được 1 lần. Nếu nút
                    không bấm được, hãy mở link này:<br><a href="%s" style="color:#5b3df5;word-break:break-all">%s</a></p>
                  <p style="color:#5b6070;font-size:14px">Nếu bạn không yêu cầu, hãy bỏ qua email này, mật khẩu của
                    bạn vẫn giữ nguyên.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(user.getEmail()), link, minutes,
                link, link);
        mailService.send(user.getEmail(), "Đặt lại mật khẩu BrainBlocks", html, text);
    }
}
