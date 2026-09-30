package com.brainblocks.backend.controller.review;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.review.ReviewResponse;
import com.brainblocks.backend.service.review.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// đánh giá của tôi; phản hồi / khiếu nại của khách nằm ở ComplaintController
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerFeedbackController {
    private final ReviewService reviewService;

    @GetMapping("/reviews/mine")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyReviews()));
    }
}
