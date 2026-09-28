package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.complaint.HandleComplaintRequest;
import com.brainblocks.backend.dto.request.review.UpdateReviewVisibilityRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.dto.response.review.ReviewResponse;
import com.brainblocks.backend.dto.response.stats.AdminStatsResponse;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.service.complaint.ComplaintService;
import com.brainblocks.backend.service.review.ReviewService;
import com.brainblocks.backend.service.stats.AdminStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// kiểm duyệt đánh giá, xử lý khiếu nại (đề 2.10, 2.11) và thống kê (đề 2.14)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminFeedbackController {
    private final ReviewService reviewService;
    private final ComplaintService complaintService;
    private final AdminStatsService adminStatsService;

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getReviews(
            @RequestParam(required = false) Boolean visible,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsForAdmin(visible, page, size)));
    }

    @PatchMapping("/reviews/{id}/visibility")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReviewVisibility(
            @PathVariable Long id, @Valid @RequestBody UpdateReviewVisibilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Review updated", reviewService.updateVisibility(id, request)));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<PageResponse<ComplaintResponse>>> getComplaints(
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getComplaintsForAdmin(status, page, size)));
    }

    @GetMapping("/complaints/{id}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> getComplaint(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getComplaint(id)));
    }

    @PatchMapping("/complaints/{id}")
    public ResponseEntity<ApiResponse<ComplaintResponse>> handleComplaint(
            @PathVariable Long id, @Valid @RequestBody HandleComplaintRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Complaint updated", complaintService.handleComplaint(id, request)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(adminStatsService.getStats()));
    }
}
