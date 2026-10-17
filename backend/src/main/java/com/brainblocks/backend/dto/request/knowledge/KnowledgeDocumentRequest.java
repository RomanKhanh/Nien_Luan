package com.brainblocks.backend.dto.request.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeDocumentRequest(@NotBlank @Size(max = 255) String title) {}
