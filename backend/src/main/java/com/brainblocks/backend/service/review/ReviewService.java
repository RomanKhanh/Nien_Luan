package com.brainblocks.backend.service.review;

import com.brainblocks.backend.dto.request.review.ReviewRequest;
import com.brainblocks.backend.dto.request.review.UpdateReviewVisibilityRequest;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.review.ReviewResponse;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Review;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.OrderItemRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ReviewRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// đánh giá sản phẩm (đề 2.6) và kiểm duyệt đánh giá (đề 2.11)
@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(Long productId, int page, int size) {
        Product product = findActiveProduct(productId);
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(reviewRepository.findByProductIdAndVisibleTrue(product.getId(), pageable),
                review -> toResponse(review, product));
    }

    // khách chỉ đánh giá sản phẩm đã nhận hàng (có trong đơn DELIVERED), mỗi sản phẩm một lần
    @Transactional
    public ReviewResponse createReview(Long productId, ReviewRequest request) {
        Customer customer = customerRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new AccessDeniedException("Only customers can review products"));
        Product product = findActiveProduct(productId);
        if (!orderItemRepository.existsPurchase(customer.getId(), product.getId(), OrderStatus.DELIVERED)) {
            throw new IllegalArgumentException("You can only review products from delivered orders");
        }
        if (reviewRepository.existsByCustomerIdAndProductId(customer.getId(), product.getId())) {
            throw new IllegalArgumentException("You have already reviewed this product");
        }
        Review review = reviewRepository.save(Review.builder()
                .customer(customer)
                .product(product)
                .rating(request.rating())
                .comment(blankToNull(request.comment()))
                .build());
        notificationService.notifyAdmins(NotificationType.NEW_REVIEW,
                "Đánh giá mới " + review.getRating() + "★ cho " + product.getName(),
                customer.getFullName() + (review.getComment() == null ? " chưa viết nhận xét." : ": " + review.getComment()),
                "/admin/reviews");
        return toResponse(review, product);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews() {
        return reviewRepository.findByCustomerIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId()).stream()
                .map(review -> toResponse(review, review.getProduct()))
                .toList();
    }

    // ===== admin =====

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getReviewsForAdmin(Boolean visible, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(reviewRepository.findForAdmin(visible, pageable),
                review -> toResponse(review, review.getProduct()));
    }

    // ẩn đánh giá không phù hợp thay vì xóa, để còn khôi phục khi ẩn nhầm
    @Transactional
    public ReviewResponse updateVisibility(Long reviewId, UpdateReviewVisibilityRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setVisible(request.visible());
        return toResponse(review, review.getProduct());
    }

    private Product findActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ReviewResponse toResponse(Review review, Product product) {
        return new ReviewResponse(review.getId(), product.getId(), product.getName(),
                review.getCustomer().getFullName(), review.getRating(), review.getComment(),
                review.isVisible(), review.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
