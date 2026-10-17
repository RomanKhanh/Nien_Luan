package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.dto.response.auth.LoginResponse;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.AuthService;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.util.EmailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Đăng nhập bằng mã 6 số gửi qua email, thay cho mật khẩu.
 * <ul>
 *   <li>Gửi mã: chỉ gửi khi email thuộc tài khoản đang hoạt động, nhưng API luôn trả cùng một câu (kể cả cooldown),
 *       nên không dò được email nào đã đăng ký.</li>
 *   <li>Mã giữ trong bộ nhớ, chỉ lưu SHA-256 (OneTimeCodes), hết hạn sau app.login-otp.expiry-minutes phút; mã mới
 *       thay mã cũ. Mỗi email gửi lại sau app.login-otp.cooldown-seconds giây.</li>
 *   <li>Sai MAX_ATTEMPTS lần thì hủy mã; đúng thì hủy mã (dùng 1 lần) và phát JWT như /api/auth/login. Kiểm tra mã
 *       nguyên tử theo từng email (ConcurrentHashMap.compute) như EmailVerificationService.</li>
 * </ul>
 * Khởi động lại máy chủ thì các mã đang chờ mất, khách xin mã mới.
 */
@Service
public class LoginOtpService {
    static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final MailService mailService;
    private final AuthService authService;
    private final Clock clock;
    private final Duration validity;
    private final Duration cooldown;
    // email (đã chuẩn hóa) -> mã đang chờ
    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();
    // email -> lần yêu cầu gần nhất, ghi cho MỌI email (kể cả chưa đăng ký) để cooldown không lộ email nào có tài khoản
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    private record PendingCode(String codeHash, Instant expiresAt, int failedAttempts) {}

    private enum Outcome { MATCH, WRONG, TOO_MANY, MISSING }

    @Autowired
    public LoginOtpService(UserRepository userRepository, MailService mailService, AuthService authService,
                           @Value("${app.login-otp.expiry-minutes:5}") long expiryMinutes,
                           @Value("${app.login-otp.cooldown-seconds:60}") long cooldownSeconds) {
        this(userRepository, mailService, authService, Clock.systemUTC(), expiryMinutes, cooldownSeconds);
    }

    // test truyền đồng hồ giả để kiểm tra hết hạn / cooldown mà không phải chờ thật
    LoginOtpService(UserRepository userRepository, MailService mailService, AuthService authService, Clock clock,
                    long expiryMinutes, long cooldownSeconds) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.authService = authService;
        this.clock = clock;
        this.validity = Duration.ofMinutes(expiryMinutes);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** Gửi mã đăng nhập nếu email thuộc tài khoản đang hoạt động; người gọi luôn nhận cùng một kết quả. */
    @Transactional(readOnly = true)
    public void sendCode(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        mailService.ensureCanSend();
        Instant now = clock.instant();
        pending.values().removeIf(code -> code.expiresAt().isBefore(now));
        lastSentAt.values().removeIf(at -> at.plus(cooldown).isBefore(now));

        // giữ chỗ gửi nguyên tử: 2 request song song cho cùng email thì chỉ 1 request được gửi
        boolean[] allowed = {false};
        lastSentAt.compute(email, (key, previous) -> {
            if (previous != null && previous.plus(cooldown).isAfter(now)) {
                return previous;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            throw new IllegalArgumentException("Please wait before requesting a new code");
        }

        Optional<User> user = userRepository.findByEmail(email).filter(User::isEnabled);
        if (user.isEmpty()) {
            return; // email chưa đăng ký hoặc bị khóa: không gửi gì, trả lời như đã gửi
        }
        String code = OneTimeCodes.generate();
        pending.put(email, new PendingCode(OneTimeCodes.hash(email, code), now.plus(validity), 0));
        sendMail(user.get(), code);
    }

    /** Kiểm tra mã; đúng thì hủy mã và phát JWT đăng nhập. */
    @Transactional
    public LoginResponse verify(String rawEmail, String code) {
        String email = EmailUtils.normalize(rawEmail);
        String givenHash = code == null ? null : OneTimeCodes.hash(email, code.trim());
        Instant now = clock.instant();
        Outcome[] outcome = {Outcome.MISSING};
        pending.compute(email, (key, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                outcome[0] = Outcome.MISSING;
                return null;
            }
            if (OneTimeCodes.matches(current.codeHash(), givenHash)) {
                outcome[0] = Outcome.MATCH;
                return null; // dùng 1 lần
            }
            int failed = current.failedAttempts() + 1;
            if (failed >= MAX_ATTEMPTS) {
                outcome[0] = Outcome.TOO_MANY;
                return null;
            }
            outcome[0] = Outcome.WRONG;
            return new PendingCode(current.codeHash(), current.expiresAt(), failed);
        });
        switch (outcome[0]) {
            case MATCH -> { }
            case WRONG -> throw new IllegalArgumentException("Login code is incorrect");
            case TOO_MANY -> throw new IllegalArgumentException("Too many wrong codes, please request a new code");
            case MISSING -> throw invalidCode();
        }
        User user = userRepository.findByEmail(email).orElseThrow(LoginOtpService::invalidCode);
        // bị khóa sau khi đã nhận mã
        if (!user.isEnabled()) {
            throw new DisabledException("Account is locked");
        }
        return authService.issueToken(user);
    }

    private static IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("Login code is invalid or has expired");
    }

    private void sendMail(User user, String code) {
        long minutes = validity.toMinutes();
        String text = "Chào " + user.getFullName() + ",\n\n"
                + "Mã đăng nhập BrainBlocks của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong " + minutes + " phút và chỉ dùng được 1 lần. Nếu bạn không yêu cầu, hãy bỏ qua "
                + "email này, tài khoản của bạn vẫn an toàn.\n\n"
                + "BrainBlocks";
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1f2433">
                  <h2 style="color:#5b3df5;margin:0 0 16px">BrainBlocks</h2>
                  <p>Chào <b>%s</b>,</p>
                  <p>Mã đăng nhập tài khoản của bạn:</p>
                  <p style="font-size:34px;font-weight:bold;letter-spacing:10px;margin:24px 0;color:#1f2433">%s</p>
                  <p style="color:#5b6070;font-size:14px">Mã có hiệu lực trong %d phút và chỉ dùng được 1 lần. Không
                    chia sẻ mã này cho người khác. Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(user.getFullName()), code, minutes);
        // mã chỉ nằm trong nội dung, không nằm trong tiêu đề (tiêu đề hay bị ghi log, hiện ở thông báo điện thoại)
        mailService.send(user.getEmail(), "Mã đăng nhập BrainBlocks", html, text);
    }
}
