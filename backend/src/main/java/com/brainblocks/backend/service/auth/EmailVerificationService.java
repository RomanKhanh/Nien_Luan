package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.exception.DuplicateEmailException;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.service.mail.MailService;
import com.brainblocks.backend.util.EmailUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mã xác nhận email khi đăng ký: gửi mã 6 số về email, đăng ký phải kèm đúng mã, nên mọi tài khoản đều có email
 * thật mà chủ tài khoản mở được (để lấy lại mật khẩu).
 * <ul>
 *   <li>Mã giữ trong bộ nhớ (chỉ lưu SHA-256), hết hạn sau app.registration.code-expiry-minutes phút.</li>
 *   <li>Sai quá MAX_ATTEMPTS lần thì hủy mã. Kiểm tra mã và đếm lần sai làm nguyên tử theo từng email
 *       (ConcurrentHashMap.compute), nên gửi nhiều request đoán mã song song cũng không vượt được giới hạn.</li>
 *   <li>Mỗi email gửi lại sau app.registration.code-cooldown-seconds giây. Thời điểm gửi lưu riêng, không mất khi mã
 *       bị hủy do nhập sai, nên không lặp "sai 5 lần → gửi lại" để spam hộp thư được.</li>
 * </ul>
 * Khởi động lại máy chủ thì các mã đang chờ mất, khách xin mã mới.
 * Tắt được bằng app.registration.email-verification=false (profile test).
 */
@Service
public class EmailVerificationService {
    static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MailService mailService;
    private final boolean enabled;
    private final Duration validity;
    private final Duration cooldown;
    // email (đã chuẩn hóa) -> mã đang chờ
    private final Map<String, PendingCode> pending = new ConcurrentHashMap<>();
    // email -> lần gửi mã gần nhất, tách khỏi mã để cooldown vẫn còn khi mã bị hủy
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    private record PendingCode(String codeHash, Instant expiresAt, int failedAttempts) {}

    // kết quả kiểm tra mã, tính bên trong compute rồi mới ném lỗi ra ngoài
    private enum Outcome { MATCH, WRONG, TOO_MANY, MISSING }

    public EmailVerificationService(UserRepository userRepository, MailService mailService,
                                    @Value("${app.registration.email-verification:true}") boolean enabled,
                                    @Value("${app.registration.code-expiry-minutes:10}") long expiryMinutes,
                                    @Value("${app.registration.code-cooldown-seconds:60}") long cooldownSeconds) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.enabled = enabled;
        this.validity = Duration.ofMinutes(expiryMinutes);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** Gửi mã mới tới email chưa có tài khoản; mã cũ (nếu có) hết hiệu lực. */
    public void sendCode(String rawEmail) {
        String email = EmailUtils.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            // trang đăng ký vốn đã báo email trùng, nên báo sớm ở bước này không lộ thêm gì
            throw new DuplicateEmailException("Email already in use");
        }
        mailService.ensureCanSend();
        Instant now = Instant.now();
        pending.values().removeIf(code -> code.expiresAt().isBefore(now));
        lastSentAt.values().removeIf(at -> at.plus(cooldown).isBefore(now));

        // giữ chỗ gửi một cách nguyên tử: 2 request gửi mã song song thì chỉ 1 request được gửi
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
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        pending.put(email, new PendingCode(hash(email, code), now.plus(validity), 0));
        sendMail(email, code);
    }

    /**
     * Kiểm tra mã khi đăng ký; đúng thì hủy mã (dùng 1 lần). Sai thì tăng số lần sai, quá MAX_ATTEMPTS thì hủy mã.
     * Không làm gì khi tính năng bị tắt.
     */
    public void verify(String rawEmail, String code) {
        if (!enabled) {
            return;
        }
        String email = EmailUtils.normalize(rawEmail);
        String givenHash = code == null ? null : hash(email, code.trim());
        Instant now = Instant.now();
        Outcome[] outcome = {Outcome.MISSING};
        // kiểm tra + cập nhật trong cùng một compute: các request song song của cùng email chạy lần lượt
        pending.compute(email, (key, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                outcome[0] = Outcome.MISSING;
                return null;
            }
            if (givenHash != null && MessageDigest.isEqual(current.codeHash().getBytes(StandardCharsets.UTF_8),
                    givenHash.getBytes(StandardCharsets.UTF_8))) {
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
            case WRONG -> throw new IllegalArgumentException("Verification code is incorrect");
            case TOO_MANY -> throw new IllegalArgumentException("Too many wrong codes, please request a new code");
            case MISSING -> throw new IllegalArgumentException("Verification code is invalid or has expired");
        }
    }

    private void sendMail(String email, String code) {
        long minutes = validity.toMinutes();
        String text = "Mã xác nhận đăng ký tài khoản BrainBlocks của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong " + minutes + " phút. Nếu bạn không đăng ký, hãy bỏ qua email này.\n\n"
                + "BrainBlocks";
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#1f2433">
                  <h2 style="color:#5b3df5;margin:0 0 16px">BrainBlocks</h2>
                  <p>Mã xác nhận đăng ký tài khoản của bạn:</p>
                  <p style="font-size:34px;font-weight:bold;letter-spacing:10px;margin:24px 0;color:#1f2433">%s</p>
                  <p style="color:#5b6070;font-size:14px">Mã có hiệu lực trong %d phút. Không chia sẻ mã này cho
                    người khác. Nếu bạn không đăng ký, hãy bỏ qua email này.</p>
                </div>
                """.formatted(code, minutes);
        // mã chỉ nằm trong nội dung, không nằm trong tiêu đề (tiêu đề hay bị ghi log, hiện ở thông báo điện thoại)
        mailService.send(email, "Mã xác nhận đăng ký BrainBlocks", html, text);
    }

    // băm kèm email để mã giống nhau ở 2 email khác nhau cho 2 giá trị khác nhau
    private static String hash(String email, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
