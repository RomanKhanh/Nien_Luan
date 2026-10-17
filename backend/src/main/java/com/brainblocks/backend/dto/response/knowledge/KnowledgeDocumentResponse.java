package com.brainblocks.backend.dto.response.knowledge;

import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(Long id, String title, String fileUrl, String fileType,
        String status, LocalDateTime uploadedAt, String uploadedByName, long chunkCount) {}
