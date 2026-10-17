package com.brainblocks.backend.config;

import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.ChatbotConfig;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.ChatbotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Lần chạy đầu chưa có cấu hình nào thì tạo sẵn một cấu hình active, để chatbot trả lời được ngay.
// Chạy sau AdminSeeder (ApplicationReadyEvent đến sau mọi CommandLineRunner) để đã có admin đứng tên.
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatbotConfigSeeder {
    private final ChatbotConfigRepository configs;
    private final AdminRepository admins;

    @Value("${app.chatbot.default-model:gemini-flash-latest}")
    private String defaultModel;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedDefaultConfig() {
        if (configs.count() > 0) return;
        Admin admin = admins.findAll().stream().findFirst().orElse(null);
        if (admin == null) {
            log.warn("No admin account, skip seeding default chatbot config");
            return;
        }
        configs.save(ChatbotConfig.builder().admin(admin).modelName(defaultModel)
                .temperature(0.3).maxTokens(2048).topK(4).active(true).build());
        log.info("Seeded default chatbot config with model {}", defaultModel);
    }
}
