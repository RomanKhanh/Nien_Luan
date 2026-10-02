package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Thông báo trong ứng dụng (chuông ở header khách hàng, số đếm trên menu quản trị).
 * Lớp bổ sung ngoài sơ đồ lớp v5.
 */
@Entity
@Table(
        name = "notifications",
        indexes = @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id, is_read")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    // đường dẫn trong frontend khi bấm vào thông báo, vd /orders/12
    @Column(length = 255)
    private String link;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
