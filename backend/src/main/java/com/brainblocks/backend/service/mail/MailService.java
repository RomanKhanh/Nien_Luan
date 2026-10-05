package com.brainblocks.backend.service.mail;

import com.brainblocks.backend.exception.MailNotConfiguredException;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email ở luồng riêng (@Async, bật ở MailConfig): người gọi không chờ SMTP, lỗi gửi chỉ ghi log.
 * <ul>
 *   <li>Chưa cấu hình SMTP (app.mail.username trống) thì không gửi được: người gọi phải kiểm tra
 *       {@link #ensureCanSend()} trước, để API báo lỗi 503 thay vì báo "đã gửi" mà khách không nhận được gì.</li>
 *   <li>app.mail.log-only=true (chỉ dùng cho test): không gửi thật, ghi nội dung chữ ra log.</li>
 *   <li>Log khi gửi thật chỉ ghi người nhận, không ghi tiêu đề / nội dung vì có thể chứa mã hoặc link đặt lại.</li>
 * </ul>
 */
@Slf4j
@Service
public class MailService {
    private final JavaMailSender mailSender;
    private final String username;
    private final String fromName;
    private final boolean logOnly;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.username}") String username,
                       @Value("${app.mail.from-name}") String fromName,
                       @Value("${app.mail.log-only:false}") boolean logOnly) {
        this.mailSender = mailSender;
        this.username = username.trim();
        this.fromName = fromName;
        this.logOnly = logOnly;
    }

    @PostConstruct
    void warnIfNotConfigured() {
        if (!logOnly && username.isEmpty()) {
            log.warn("SMTP is not configured (MAIL_USERNAME / MAIL_PASSWORD are empty): sign-up codes and "
                    + "password reset links cannot be sent, those requests will fail with 503");
        }
    }

    /** Ném MailNotConfiguredException (503) nếu không gửi được email nào. */
    public void ensureCanSend() {
        if (!logOnly && username.isEmpty()) {
            throw new MailNotConfiguredException();
        }
    }

    // html: nội dung chính; text: bản chữ thuần cho trình đọc mail không hiện HTML (cũng là bản ghi ra log ở log-only)
    @Async
    public void send(String to, String subject, String html, String text) {
        if (logOnly) {
            log.info("app.mail.log-only: không gửi thật. To: {} | Subject: {}\n{}", to, subject, text);
            return;
        }
        if (username.isEmpty()) {
            log.error("Could not send mail to {}: SMTP is not configured", to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("Sent mail to {}", to);
        } catch (Exception e) {
            log.error("Could not send mail to {}", to, e);
        }
    }
}
