package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.entity.KnowledgeDocument;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeIndexingRecovery {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeIndexingWorker worker;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedDocuments() {
        // Server tắt giữa chừng không để tài liệu kẹt PROCESSING mãi.
        // Chụp đủ id trước khi enqueue, vì worker hoàn tất sẽ làm thay đổi bộ lọc phân trang.
        List<Long> pending = new ArrayList<>();
        int page = 0;
        Page<KnowledgeDocument> batch;
        do {
            batch = documents.findByStatusOrderByUploadedAtDescIdDesc(DocumentStatus.PROCESSING, PageRequest.of(page++, 100));
            batch.forEach(d -> pending.add(d.getId()));
        } while (batch.hasNext());
        for (Long id : pending) {
            try { worker.process(id); }
            catch (RuntimeException ex) { worker.resetToUploaded(id); }
        }
    }
}
