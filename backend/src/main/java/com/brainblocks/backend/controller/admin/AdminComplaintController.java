package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.complaint.EvidenceHoldRequest;
import com.brainblocks.backend.dto.request.complaint.UpdateComplaintStatusRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.service.complaint.AdminComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/complaints")
@RequiredArgsConstructor
public class AdminComplaintController {
    private final AdminComplaintService adminComplaintService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ComplaintResponse>>> getComplaints(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminComplaintService.getComplaints(status, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> getComplaint(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminComplaintService.getComplaint(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateComplaintStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Complaint updated", adminComplaintService.updateStatus(id, request)));
    }

    // giữ lại / bỏ giữ bằng chứng (video, ảnh) để không bị xóa tự động
    @PatchMapping("/{id}/evidence-hold")
    public ResponseEntity<ApiResponse<ComplaintResponse>> setEvidenceHold(
            @PathVariable Long id, @Valid @RequestBody EvidenceHoldRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Evidence hold updated",
                adminComplaintService.setEvidenceHold(id, request.hold())));
    }
}