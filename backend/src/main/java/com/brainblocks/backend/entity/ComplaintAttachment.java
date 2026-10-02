package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.EvidenceKind;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * File bằng chứng khách đính kèm vào khiếu nại (video mở hàng, ảnh / video tình trạng sản phẩm).
 * File nằm trong thư mục riêng (app.complaint.evidence-dir), KHÔNG phục vụ công khai như ảnh sản phẩm;
 * chỉ chủ khiếu nại và admin tải được qua API. Lớp bổ sung ngoài sơ đồ lớp v5.
 */
@Entity
@Table(name = "complaint_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceKind kind;

    // đường dẫn tương đối trong thư mục bằng chứng, vd 12/3f2a...mp4
    @Column(nullable = false, length = 255)
    private String storedPath;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // lúc file bị xóa tự động khỏi đĩa (EvidenceRetention); bản ghi vẫn giữ để thống kê. null = file còn
    private LocalDateTime purgedAt;
}
