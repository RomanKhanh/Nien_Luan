package com.brainblocks.backend.dto.response.chat;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionDetailResponse(Long id, String title, LocalDateTime startedAt,
        LocalDateTime lastMessageAt, long messageCount, Long childProfileId, String childName,
        List<ChatMessageResponse> messages) {}
