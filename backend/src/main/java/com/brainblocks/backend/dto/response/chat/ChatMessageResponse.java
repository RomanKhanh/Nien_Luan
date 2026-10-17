package com.brainblocks.backend.dto.response.chat;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponse(Long id, String sender, String content, String topic,
        LocalDateTime createdAt, long responseTimeMs,
        List<ChatSuggestedProductResponse> suggestedProducts, List<ChatSourceResponse> sources) {}
