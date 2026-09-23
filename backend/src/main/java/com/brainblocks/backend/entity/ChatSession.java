package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tiêu đề hội thoại, thường lấy từ câu hỏi đầu tiên của người dùng
    @Column(length = 255)
    private String title;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    // Chủ của cuộc hội thoại
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Hồ sơ trẻ được chọn để tư vấn — có thể null nếu phụ huynh chat chung chung
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_profile_id")
    private ChildProfile childProfile;

    @OneToMany(
            mappedBy = "chatSession",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    // Tự gán thời điểm bắt đầu khi lưu lần đầu
    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }

    // Helper: thêm tin nhắn và giữ đồng bộ 2 chiều của quan hệ
    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setChatSession(this);
    }
}
