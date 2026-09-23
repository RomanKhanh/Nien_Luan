package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.DocumentStatus;
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
@Table(name = "knowledge_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    @Column(nullable = false, length = 20)
    private String fileType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadedAt;

    // admin đã tải tài liệu lên và quản lý nó
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private Admin admin;

    // thành phần: xóa tài liệu thì xóa luôn các đoạn đã tách
    @Builder.Default
    @OneToMany(mappedBy = "knowledgeDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> chunks = new ArrayList<>();
}
