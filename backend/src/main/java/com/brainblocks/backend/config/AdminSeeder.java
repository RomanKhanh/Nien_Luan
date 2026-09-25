package com.brainblocks.backend.config;

import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.enums.Role;
import com.brainblocks.backend.repository.UserRepository;
import com.brainblocks.backend.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Tự tạo tài khoản admin lúc khởi động nếu chưa có
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD is empty, skip seeding admin account");
            return;
        }
        // chuẩn hóa như lúc đăng nhập, nếu không ADMIN_EMAIL có chữ hoa sẽ không đăng nhập được
        String email = EmailUtils.normalize(adminEmail);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(Admin.builder()
                .fullName("Administrator")
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build());
        log.info("Seeded admin account: {}", email);
    }
}