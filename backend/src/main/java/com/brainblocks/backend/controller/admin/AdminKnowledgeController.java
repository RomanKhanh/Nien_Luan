package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.knowledge.KnowledgeDocumentRequest;
import com.brainblocks.backend.dto.request.knowledge.UpdateDocumentStatusRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.knowledge.DocumentChunkResponse;
import com.brainblocks.backend.dto.response.knowledge.KnowledgeDocumentResponse;
import com.brainblocks.backend.enums.DocumentStatus;
import com.brainblocks.backend.service.ai.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Cơ sở tri thức cho chatbot (đề 2.12). Tải lên xong tài liệu ở trạng thái PROCESSING,
// worker tách đoạn + tạo embedding chạy nền rồi chuyển sang INDEXED (lỗi thì UPLOADED để reindex).
@RestController
@RequestMapping("/api/admin/knowledge-documents")
@RequiredArgsConstructor
public class AdminKnowledgeController {
    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<KnowledgeDocumentResponse>>> getDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.getDocuments(keyword, status, page, size)));
    }

    // multipart: title + file (PDF, DOCX, TXT, MD; tối đa 10 MB, kiểm tra ở KnowledgeDocumentStorage)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> uploadDocument(
            @RequestParam("title") String title, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded",
                knowledgeBaseService.uploadDocument(new KnowledgeDocumentRequest(title), file)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> updateDocument(
            @PathVariable Long id, @Valid @RequestBody KnowledgeDocumentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Document updated", knowledgeBaseService.updateDocument(id, request)));
    }

    // multipart: file mới (cùng điều kiện lúc tải lên); đoạn cũ bị xóa, tài liệu sang PROCESSING và được index lại.
    // Tài liệu đang PROCESSING trả 409
    @PostMapping(path = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> replaceFile(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Document file replaced", knowledgeBaseService.replaceFile(id, file)));
    }

    // chỉ nhận INDEXED / DISABLED; tài liệu đang PROCESSING trả 409
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> setDocumentStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateDocumentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Document status updated",
                knowledgeBaseService.setDocumentStatus(id, request)));
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> reindexDocument(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Document queued for indexing", knowledgeBaseService.reindexDocument(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<ApiResponse<List<DocumentChunkResponse>>> getChunks(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeBaseService.getDocumentChunks(id)));
    }
}
