package com.brainblocks.backend.dto.response.chat;

import java.time.LocalDateTime;

public record AdminChatSessionSummaryResponse(Long id, String title, LocalDateTime startedAt,
        LocalDateTime lastMessageAt, long messageCount, Long childProfileId, String childName,
        Long customerId, String customerName, String customerEmail) {}
