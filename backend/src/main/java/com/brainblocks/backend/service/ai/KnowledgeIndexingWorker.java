package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.entity.DocumentChunk;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.repository.KnowledgeDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
@Slf4j
public class KnowledgeIndexingWorker {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentStorage storage;
    private final GeminiService gemini;
    private final TransactionTemplate tx;

    public KnowledgeIndexingWorker(KnowledgeDocumentRepository documents, KnowledgeDocumentStorage storage,
            GeminiService gemini, PlatformTransactionManager manager) {
        this.documents = documents;
        this.storage = storage;
        this.gemini = gemini;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async("knowledgeExecutor")
    public void process(Long id) {
        try {
            Source source = tx.execute(status -> documents.findById(id)
                    .filter(d -> d.getStatus() == DocumentStatus.PROCESSING)
                    .map(d -> new Source(d.getFileUrl(), d.getFileType())).orElse(null));
            if (source == null) return;
            List<String> texts = storage.splitText(storage.readText(source.file(), source.type()));
            // Các lời gọi mạng nằm NGOÀI transaction, không giữ connection/khóa DB khi chờ AI.
            List<float[]> vectors = gemini.embedDocuments(texts);
            tx.executeWithoutResult(status -> {
                documents.lockId(id);
                var document = documents.findById(id).orElse(null);
                if (document == null || document.getStatus() != DocumentStatus.PROCESSING) return;
                // Xóa và flush chunk cũ trước INSERT mới để không trùng (document, chunkIndex).
                document.getChunks().clear();
                documents.flush();
                for (int i = 0; i < texts.size(); i++) {
                    document.getChunks().add(DocumentChunk.builder().knowledgeDocument(document)
                            .chunkIndex(i).content(texts.get(i)).embedding(vectors.get(i)).build());
                }
                document.setStatus(DocumentStatus.INDEXED);
                documents.saveAndFlush(document);
            });
        } catch (Exception ex) {
            // Enum UML không có FAILED: trả UPLOADED để admin có thể bấm reindex lại.
            log.warn("Indexing failed for document {}: {} ({})", id, ex.getMessage(), ex.getClass().getSimpleName());
            resetToUploaded(id);
        }
    }

    public void resetToUploaded(Long id) {
        tx.executeWithoutResult(status -> {
            documents.lockId(id);
            documents.findById(id).filter(d -> d.getStatus() == DocumentStatus.PROCESSING)
                    .ifPresent(d -> d.setStatus(DocumentStatus.UPLOADED));
        });
    }

    private record Source(String file, String type) {}
}
