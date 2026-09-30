package com.brainblocks.backend.controller.complaint;

import com.brainblocks.backend.dto.request.complaint.CreateComplaintRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.service.complaint.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ComplaintController {
    private final ComplaintService complaintService;

    @PostMapping("/api/orders/{id}/complaints")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaint(
            @PathVariable Long id, @Valid @RequestBody CreateComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint submitted", complaintService.createComplaint(id, request)));
    }

    @GetMapping("/api/complaints")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getMyComplaints() {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getMyComplaints()));
    }
}