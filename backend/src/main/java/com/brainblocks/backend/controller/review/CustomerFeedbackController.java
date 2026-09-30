package com.brainblocks.backend.controller.review;

import com.brainblocks.backend.dto.request.complaint.ComplaintRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.complaint.ComplaintResponse;
import com.brainblocks.backend.dto.response.review.ReviewResponse;
import com.brainblocks.backend.service.complaint.ComplaintService;
import com.brainblocks.backend.service.review.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// đánh giá của tôi và phản hồi / khiếu nại / yêu cầu đổi - trả - hủy của khách hàng
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerFeedbackController {
    private final ReviewService reviewService;
    private final ComplaintService complaintService;

    @GetMapping("/reviews/mine")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyReviews()));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<ComplaintResponse>>> getMyComplaints() {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getMyComplaints()));
    }

    @PostMapping("/complaints")
    public ResponseEntity<ApiResponse<ComplaintResponse>> createComplaint(@Valid @RequestBody ComplaintRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Complaint sent", complaintService.createComplaint(request)));
    }
}
