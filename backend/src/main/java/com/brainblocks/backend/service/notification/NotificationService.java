package com.brainblocks.backend.service.notification;

import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.notification.NotificationResponse;
import com.brainblocks.backend.dto.response.notification.UnreadCountResponse;
import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.Notification;
import com.brainblocks.backend.entity.User;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.NotificationRepository;
import com.brainblocks.backend.repository.NotificationRepository.TypeCount;
import com.brainblocks.backend.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thông báo trong ứng dụng. Các nghiệp vụ (đơn hàng, khiếu nại, đánh giá) gọi notify / notifyAdmins
 * trong cùng transaction của mình, nên thao tác lỗi rollback thì thông báo cũng không được tạo.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final AdminRepository adminRepository;
    private final CurrentUserProvider currentUserProvider;

    public void notify(User recipient, NotificationType type, String title, String message, String link) {
        notificationRepository.save(build(recipient, type, title, message, link));
    }

    // gửi cho mọi admin đang hoạt động (mỗi admin một bản để đánh dấu đã đọc riêng)
    public void notifyAdmins(NotificationType type, String title, String message, String link) {
        List<Notification> notifications = adminRepository.findAll().stream()
                .filter(Admin::isEnabled)
                .map(admin -> build(admin, type, title, message, link))
                .toList();
        notificationRepository.saveAll(notifications);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(
                notificationRepository.findByRecipientId(currentUserProvider.getCurrentUserId(), pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        Map<String, Long> byType = notificationRepository
                .countUnreadByType(currentUserProvider.getCurrentUserId()).stream()
                .collect(Collectors.toMap(row -> row.getType().name(), TypeCount::getTotal));
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new UnreadCountResponse(total, byType);
    }

    @Transactional
    public void markRead(Long id) {
        // thông báo của người khác hoặc không tồn tại: không làm gì, không lộ thông tin
        notificationRepository.markRead(id, currentUserProvider.getCurrentUserId());
    }

    // types rỗng = mọi loại (vd admin mở trang Đánh giá thì chỉ đánh dấu các thông báo NEW_REVIEW)
    @Transactional
    public int markAllRead(Collection<NotificationType> types) {
        Long userId = currentUserProvider.getCurrentUserId();
        return types == null || types.isEmpty()
                ? notificationRepository.markAllRead(userId)
                : notificationRepository.markAllReadByTypes(userId, types);
    }

    private Notification build(User recipient, NotificationType type, String title, String message, String link) {
        return Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .build();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(), n.getLink(),
                n.isRead(), n.getCreatedAt());
    }
}
