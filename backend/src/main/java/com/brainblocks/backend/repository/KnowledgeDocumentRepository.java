package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.KnowledgeDocument;
import com.brainblocks.backend.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

// Specification hỗ trợ kết hợp bộ lọc (tên tài liệu, trạng thái...) khi triển khai service.
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long>,
        JpaSpecificationExecutor<KnowledgeDocument> {

    @Query(value = "select id from knowledge_documents where id = :id for update", nativeQuery = true)
    Optional<Long> lockId(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "admin")
    Page<KnowledgeDocument> findAll(Specification<KnowledgeDocument> spec, Pageable pageable);

    // Lọc tài liệu theo UPLOADED, PROCESSING, INDEXED hoặc DISABLED.
    @EntityGraph(attributePaths = "admin")
    Page<KnowledgeDocument> findByStatusOrderByUploadedAtDescIdDesc(DocumentStatus status, Pageable pageable);
}
