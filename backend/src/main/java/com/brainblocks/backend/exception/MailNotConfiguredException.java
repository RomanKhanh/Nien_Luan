package com.brainblocks.backend.exception;

// chưa cấu hình SMTP (app.mail.username trống) mà cũng không bật app.mail.log-only: không gửi được email nào
public class MailNotConfiguredException extends RuntimeException {
    public MailNotConfiguredException() {
        super("Email sending is not configured");
    }
}
