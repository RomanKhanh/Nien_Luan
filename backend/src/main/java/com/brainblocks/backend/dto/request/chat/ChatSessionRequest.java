package com.brainblocks.backend.dto.request.chat;

import jakarta.validation.constraints.Positive;

// null = tư vấn chung; service kiểm tra bé thuộc đúng khách đang đăng nhập.
public record ChatSessionRequest(@Positive Long childProfileId) {}
