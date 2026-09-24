package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    // Chủ của cuộc hội thoại
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // Hồ sơ trẻ được chọn để tư vấn - có thể null nếu phụ huynh chat chung chung
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_profile_id")
    private ChildProfile childProfile;

    // thành phần: xóa hội thoại thì xóa luôn tin nhắn
    @Builder.Default
    @OneToMany(
            mappedBy = "chatSession",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    // Helper: thêm tin nhắn và giữ đồng bộ 2 chiều của quan hệ
    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setChatSession(this);
    }
}
