package com.brainblocks.backend.dto.request.knowledge;

import com.brainblocks.backend.enums.DocumentStatus;
import jakarta.validation.constraints.NotNull;

// Service chỉ chấp nhận INDEXED hoặc DISABLED; PROCESSING do worker quản lý.
public record UpdateDocumentStatusRequest(@NotNull DocumentStatus status) {}
