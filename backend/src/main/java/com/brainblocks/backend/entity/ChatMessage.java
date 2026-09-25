package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.SenderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SenderType sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // chủ đề tư vấn, dùng cho thống kê câu hỏi / chủ đề phổ biến
    @Column(length = 50)
    private String topic;

    // sản phẩm chatbot gợi ý trong tin nhắn này.
    // chỉ tin nhắn sender = BOT mới có; productId do AI trả về phải tồn tại và active (kiểm tra ở service)
    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "chat_message_products",
            joinColumns = @JoinColumn(name = "chat_message_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> suggestedProducts = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // thời gian bot phản hồi; tin nhắn của USER để 0
    @Column(nullable = false)
    private long responseTimeMs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;
}
