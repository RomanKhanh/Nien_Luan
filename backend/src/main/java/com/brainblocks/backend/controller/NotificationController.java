package com.brainblocks.backend.controller;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.notification.NotificationResponse;
import com.brainblocks.backend.dto.response.notification.UnreadCountResponse;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// thông báo của chính người đang đăng nhập (khách hàng hoặc admin); SecurityConfig yêu cầu đăng nhập
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getMyNotifications(page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    // types=NEW_REVIEW,NEW_ORDER: chỉ đánh dấu các loại này; bỏ trống = tất cả
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(
            @RequestParam(required = false) List<NotificationType> types) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAllRead(types)));
    }
}
