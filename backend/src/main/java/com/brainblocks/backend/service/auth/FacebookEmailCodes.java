package com.brainblocks.backend.service.auth;

import com.brainblocks.backend.service.mail.MailService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** OTP is bound to both the verified Facebook identity and the normalized email. No access tokens are stored. */
@Service
public class FacebookEmailCodes {
    private final MailService mail;
    private final Clock clock;
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Instant> sent = new HashMap<>();
    private record Pending(String email, String hash, Instant expires, int failures) {}

    @org.springframework.beans.factory.annotation.Autowired
    public FacebookEmailCodes(MailService mail) { this(mail, Clock.systemUTC()); }
    FacebookEmailCodes(MailService mail, Clock clock) { this.mail = mail; this.clock = clock; }

    public synchronized void send(String subject, String email) {
        mail.ensureCanSend();
        Instant now = clock.instant();
        pending.values().removeIf(p -> !p.expires().isAfter(now));
        sent.values().removeIf(at -> !at.plusSeconds(60).isAfter(now));
        if (sent.containsKey("subject:" + subject) || sent.containsKey("email:" + email)) {
            throw new IllegalArgumentException("Please wait before requesting a new code");
        }
        String code = OneTimeCodes.generate();
        pending.put(subject, new Pending(email, OneTimeCodes.hash(subject + ":" + email, code), now.plusSeconds(300), 0));
        sent.put("subject:" + subject, now);
        sent.put("email:" + email, now);
        String text = "Mã xác minh email để đăng nhập hoặc liên kết Facebook với BrainBlocks: " + code
                + "\nMã có hiệu lực 5 phút. Chỉ nhập mã nếu chính bạn yêu cầu liên kết Facebook. Không chia sẻ mã này.";
        mail.send(email, "Xác minh email đăng nhập Facebook - BrainBlocks", "<p>" + text + "</p>", text);
    }

    public synchronized void verify(String subject, String email, String code) {
        Pending value = pending.get(subject);
        if (value == null || !value.expires().isAfter(clock.instant())) {
            pending.remove(subject);
            throw new IllegalArgumentException("Verification code is invalid or has expired");
        }
        if (value.email().equals(email) && OneTimeCodes.matches(value.hash(), OneTimeCodes.hash(subject + ":" + email, code))) {
            pending.remove(subject);
            return;
        }
        if (value.failures() >= 4) {
            pending.remove(subject);
            throw new IllegalArgumentException("Too many wrong codes, please request a new code");
        }
        pending.put(subject, new Pending(value.email(), value.hash(), value.expires(), value.failures() + 1));
        throw new IllegalArgumentException("Verification code is incorrect");
    }
}
