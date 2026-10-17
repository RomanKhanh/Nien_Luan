package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.DocumentChunk;
import com.brainblocks.backend.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    long countByKnowledgeDocumentId(Long documentId);
    boolean existsByKnowledgeDocumentStatusAndEmbeddingIsNotNull(DocumentStatus status);
    long countByKnowledgeDocumentIdAndEmbeddingIsNull(Long documentId);

    @Query("select c.knowledgeDocument.id as documentId, count(c) as total from DocumentChunk c "
            + "where c.knowledgeDocument.id in :ids group by c.knowledgeDocument.id")
    List<ChunkCount> countForDocuments(@Param("ids") List<Long> ids);

    interface ChunkCount {
        Long getDocumentId();
        Long getTotal();
    }

    // Các đoạn của một tài liệu theo đúng thứ tự tách trong tài liệu gốc.
    List<DocumentChunk> findAllByKnowledgeDocumentIdOrderByChunkIndexAsc(Long knowledgeDocumentId);

    // Tìm đoạn gần câu hỏi nhất (RAG) dùng pgvector, xem VectorSearchRepository.
}
