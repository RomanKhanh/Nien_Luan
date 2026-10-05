package com.brainblocks.backend.service.mail;

import com.brainblocks.backend.exception.MailNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Chưa cấu hình SMTP thì không báo "đã gửi": ensureCanSend ném lỗi (API trả 503), trừ khi bật log-only cho test.
 */
class MailServiceTest {

    @Test
    void notConfiguredRefusesToSend() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailService mail = new MailService(sender, "  ", "BrainBlocks", false);
        assertThatThrownBy(mail::ensureCanSend).isInstanceOf(MailNotConfiguredException.class)
                .hasMessage("Email sending is not configured");
        mail.send("a@test.local", "subject", "<p>html</p>", "text");
        verify(sender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @Test
    void configuredOrLogOnlyCanSend() {
        assertThatCode(new MailService(mock(JavaMailSender.class), "shop@gmail.com", "BrainBlocks", false)::ensureCanSend)
                .doesNotThrowAnyException();
        assertThatCode(new MailService(mock(JavaMailSender.class), "", "BrainBlocks", true)::ensureCanSend)
                .doesNotThrowAnyException();
    }
}
