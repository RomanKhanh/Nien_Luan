package com.brainblocks.backend.dto.request.chat;

import jakarta.validation.constraints.*;

// Boxed types giúp @NotNull phát hiện field bị bỏ quên trong JSON.
// maxTokens tối thiểu 1024: token suy luận của model Gemini cũng tính vào giới hạn, đặt thấp sẽ cắt cụt JSON trả lời.
public record ChatbotConfigRequest(
        @NotBlank @Size(max = 100) String modelName,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double temperature,
        @NotNull @Min(1024) @Max(8192) Integer maxTokens,
        @NotNull @Min(1) @Max(20) Integer topK,
        @Size(max = 4000) String systemPrompt
) {}
