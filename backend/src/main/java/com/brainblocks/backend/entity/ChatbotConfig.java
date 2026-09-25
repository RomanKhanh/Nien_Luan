package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false)
    private double temperature;

    @Column(nullable = false)
    private int maxTokens;

    // số đoạn văn bản lấy ra từ kho tri thức cho mỗi câu hỏi
    @Column(nullable = false)
    private int topK;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    // chỉ một cấu hình active tại một thời điểm (service bật cái này thì tắt cái khác).
    // có default để thêm cột vào bảng đã có dữ liệu không bị lỗi NOT NULL
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean active = false;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // admin đã tạo cấu hình này
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;
}
