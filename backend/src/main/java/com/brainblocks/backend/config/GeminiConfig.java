package com.brainblocks.backend.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    // Thiếu GOOGLE_API_KEY vẫn khởi động được để làm các cụm khác; chỉ lời gọi AI mới báo lỗi 503.
    // Bean trả null thì GeminiService nhận được "không có client" qua ObjectProvider.
    @Bean
    public Client geminiClient(@Value("${GOOGLE_API_KEY:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return null;
        return Client.builder()
                .apiKey(apiKey.trim())
                .build();
    }
}
