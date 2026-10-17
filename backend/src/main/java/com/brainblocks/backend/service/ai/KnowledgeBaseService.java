package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.dto.request.knowledge.*;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.knowledge.*;
import com.brainblocks.backend.dto.response.chat.ChatSourceResponse;
import com.brainblocks.backend.entity.DocumentChunk;
import com.brainblocks.backend.entity.KnowledgeDocument;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.exception.ChatbotConflictException;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.*;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.util.VectorMath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {
    private final KnowledgeDocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final AdminRepository admins;
    private final CurrentUserProvider currentUser;
    private final KnowledgeDocumentStorage storage;
    private final KnowledgeIndexingWorker worker;
    private final GeminiService gemini;
    private final VectorSearchRepository vectorSearch;

    static final int SNIPPET_LENGTH = 300;
    // Cosine tối thiểu để một đoạn được coi là liên quan; embedding Gemini cho văn bản lạc đề vẫn thường > 0.
    @Value("${app.knowledge.min-score:0.6}")
    private double minScore = 0.6;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDocumentResponse> getDocuments(String keyword, DocumentStatus status, int page, int size) {
        String pattern = "%" + escapeLike(keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        Specification<KnowledgeDocument> filter = (root, query, cb) -> cb.and(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        var result = documents.findAll(filter, PageRequest.of(page, Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"))));
        List<Long> ids = result.getContent().stream().map(KnowledgeDocument::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of() : chunks.countForDocuments(ids).stream()
                .collect(Collectors.toMap(DocumentChunkRepository.ChunkCount::getDocumentId,
                        DocumentChunkRepository.ChunkCount::getTotal));
        return PageResponse.of(result, d -> toResponse(d, counts.getOrDefault(d.getId(), 0L)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentResponse uploadDocument(@Valid KnowledgeDocumentRequest request, MultipartFile file) {
        var admin = admins.findById(currentUser.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        String type = storage.fileType(file);
        String stored = storage.store(file, type);
        // Transaction rollback phải dọn file vừa ghi để không để lại file mồ côi.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(stored);
            }
        });
        KnowledgeDocument document = documents.saveAndFlush(KnowledgeDocument.builder().admin(admin)
                .title(request.title().trim()).fileUrl(stored).fileType(type).status(DocumentStatus.PROCESSING).build());
        queueAfterCommit(document.getId());
        return toResponse(document, 0);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentResponse updateDocument(Long id, @Valid KnowledgeDocumentRequest request) {
        KnowledgeDocument document = locked(id);
        document.setTitle(request.title().trim());
        return toResponse(document, chunks.countByKnowledgeDocumentId(id));
    }

    // Thay file của tài liệu đã có: giữ id và tiêu đề, index lại từ file mới.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentResponse replaceFile(Long id, MultipartFile file) {
        KnowledgeDocument document = locked(id);
        requireNotProcessing(document);
        String type = storage.fileType(file);
        String stored = storage.store(file, type);
        String previous = document.getFileUrl();
        // Commit thì bỏ file cũ, rollback thì bỏ file vừa ghi.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                deleteQuietly(status == STATUS_COMMITTED ? previous : stored);
            }
        });
        // Đoạn của file cũ không còn đúng nội dung: xóa ngay, để index file mới lỗi cũng không bật lại được bản cũ.
        document.getChunks().clear();
        document.setFileUrl(stored);
        document.setFileType(type);
        document.setStatus(DocumentStatus.PROCESSING);
        documents.saveAndFlush(document);
        queueAfterCommit(id);
        return toResponse(document, 0);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentResponse setDocumentStatus(Long id, @Valid UpdateDocumentStatusRequest request) {
        KnowledgeDocument document = locked(id);
        requireNotProcessing(document);
        if (request.status() != DocumentStatus.INDEXED && request.status() != DocumentStatus.DISABLED) {
            throw new IllegalArgumentException("Only INDEXED or DISABLED can be selected");
        }
        long count = chunks.countByKnowledgeDocumentId(id);
        if (request.status() == DocumentStatus.INDEXED && (count == 0 || chunks.countByKnowledgeDocumentIdAndEmbeddingIsNull(id) > 0)) {
            throw new ChatbotConflictException("Document must be successfully indexed first");
        }
        document.setStatus(request.status());
        return toResponse(document, count);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentResponse reindexDocument(Long id) {
        KnowledgeDocument document = locked(id);
        requireNotProcessing(document);
        document.setStatus(DocumentStatus.PROCESSING);
        queueAfterCommit(id);
        return toResponse(document, chunks.countByKnowledgeDocumentId(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = locked(id);
        requireNotProcessing(document);
        String stored = document.getFileUrl();
        documents.delete(document); // Cascade trong entity xóa cả DocumentChunk.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(stored); }
        });
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<DocumentChunkResponse> getDocumentChunks(Long id) {
        if (!documents.existsById(id)) throw new ResourceNotFoundException("Knowledge document not found");
        return chunks.findAllByKnowledgeDocumentIdOrderByChunkIndexAsc(id).stream()
                .map(c -> new DocumentChunkResponse(c.getId(), c.getChunkIndex(), c.getContent(), c.getEmbedding() != null)).toList();
    }

    // Truy xuất nội bộ cho ChatService; chỉ xét tài liệu INDEXED, không cần quyền admin.
    // Đoạn dưới ngưỡng tương đồng bị bỏ, nên câu chào hỏi không kéo theo "nguồn" không liên quan.
    public List<RetrievedChunk> retrieve(String question, int topK) {
        if (!hasIndexedChunks()) return List.of();
        return retrieve(gemini.embedQuery(question), topK);
    }

    // Kho tri thức có đoạn dùng được chưa; chưa có thì không cần tốn một lần embed câu hỏi.
    public boolean hasIndexedChunks() {
        return chunks.existsByKnowledgeDocumentStatusAndEmbeddingIsNotNull(DocumentStatus.INDEXED);
    }

    // Top-K tính ngay trong PostgreSQL bằng pgvector; ngưỡng cosine min-score đổi thành khoảng cách 1 - min-score.
    public List<RetrievedChunk> retrieve(float[] queryVector, int topK) {
        if (queryVector == null) return List.of();
        return vectorSearch.nearestChunks(queryVector, Math.clamp(topK, 1, 20), VectorMath.toDistance(minScore)).stream()
                .map(m -> new RetrievedChunk(m.chunkId(), m.documentId(), m.documentTitle(), m.content(),
                        VectorMath.toSimilarity(m.distance())))
                .toList();
    }

    // content đầy đủ để đưa vào ngữ cảnh AI; toSource() chỉ giữ đoạn trích ngắn để hiển thị và lưu lịch sử.
    public record RetrievedChunk(Long chunkId, Long documentId, String documentTitle, String content, double score) {
        public ChatSourceResponse toSource() {
            String snippet = content.length() <= SNIPPET_LENGTH ? content : content.substring(0, SNIPPET_LENGTH).strip() + "…";
            return new ChatSourceResponse(documentId, documentTitle, snippet);
        }
    }

    private KnowledgeDocument locked(Long id) {
        documents.lockId(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
        return documents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
    }

    private void requireNotProcessing(KnowledgeDocument document) {
        if (document.getStatus() == DocumentStatus.PROCESSING) throw new ChatbotConflictException("Document is still being processed");
    }

    private void queueAfterCommit(Long id) {
        // Worker chỉ đọc bản ghi sau khi transaction tạo/cập nhật tài liệu đã commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { worker.process(id); }
                catch (RuntimeException ex) { worker.resetToUploaded(id); }
            }
        });
    }

    private void deleteQuietly(String stored) {
        try { storage.delete(stored); }
        catch (RuntimeException ex) { log.warn("Could not clean up knowledge file ({})", ex.getClass().getSimpleName()); }
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument d, long count) {
        return new KnowledgeDocumentResponse(d.getId(), d.getTitle(), d.getFileUrl(), d.getFileType(),
                d.getStatus().name(), d.getUploadedAt(), d.getAdmin().getFullName(), count);
    }

    static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }
}
