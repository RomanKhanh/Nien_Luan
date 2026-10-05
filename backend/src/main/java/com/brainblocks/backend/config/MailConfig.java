package com.brainblocks.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Properties;

/**
 * SMTP gửi email (cấu hình app.mail.*, mặc định Gmail cổng 587 + STARTTLS). Tự cấu hình thay vì dùng spring.mail.*
 * để bỏ dấu cách trong mật khẩu ứng dụng Gmail ("abcd efgh ijkl mnop" như Google hiển thị).
 * Bật @Async để MailService gửi ở luồng riêng: request không phải chờ SMTP.
 */
@Configuration
@EnableAsync
public class MailConfig {

    @Bean
    public JavaMailSender mailSender(@Value("${app.mail.host}") String host,
                                     @Value("${app.mail.port}") int port,
                                     @Value("${app.mail.username}") String username,
                                     @Value("${app.mail.password}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username.trim());
        sender.setPassword(password.replaceAll("\s", ""));
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
