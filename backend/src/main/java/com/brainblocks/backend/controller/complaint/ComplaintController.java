package com.brainblocks.backend.controller.complaint;

import com.brainblocks.backend.dto.request.complaint.CreateComplaintRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.service.complaint.ComplaintService;
import com.brainblocks.backend.service.complaint.ComplaintService.EvidenceFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ComplaintController {
    private final ComplaintService complaintService;

    // JSON, không kèm file: dùng cho yêu cầu hủy đơn / phản hồi khác (đổi / trả / chất lượng bắt buộc video -> 400)
    @PostMapping(path = "/api/orders/{id}/complaints", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaint(
            @PathVariable Long id, @Valid @RequestBody CreateComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Complaint submitted",
                complaintService.createComplaint(id, request, null, List.of())));
    }

    // multipart/form-data: part "request" (JSON), "unboxingVideo" (video mở hàng), "conditionFiles" (ảnh / video tình trạng)
    @PostMapping(path = "/api/orders/{id}/complaints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaintWithEvidence(
            @PathVariable Long id,
            @Valid @RequestPart("request") CreateComplaintRequest request,
            @RequestPart(value = "unboxingVideo", required = false) MultipartFile unboxingVideo,
            @RequestPart(value = "conditionFiles", required = false) List<MultipartFile> conditionFiles) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Complaint submitted",
                complaintService.createComplaint(id, request, unboxingVideo, conditionFiles)));
    }

    @GetMapping("/api/complaints")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getMyComplaints() {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getMyComplaints()));
    }

    // file bằng chứng: chủ khiếu nại hoặc admin (kiểm tra trong service); không cache ở proxy dùng chung
    @GetMapping("/api/complaints/{complaintId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> getAttachment(@PathVariable Long complaintId, @PathVariable Long attachmentId) {
        EvidenceFile file = complaintService.loadAttachment(complaintId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
}
