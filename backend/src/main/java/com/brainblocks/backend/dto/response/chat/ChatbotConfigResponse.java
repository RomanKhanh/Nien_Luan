package com.brainblocks.backend.dto.response.chat;

import java.time.LocalDateTime;

// DTO không chứa API key, cũng không trả entity Admin ra phía client.
public record ChatbotConfigResponse(Long id, String modelName, double temperature, int maxTokens,
        int topK, String systemPrompt, boolean active, LocalDateTime updatedAt, String adminName) {}
