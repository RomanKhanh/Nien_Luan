package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "document_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_chunks_document_index",
                columnNames = {"knowledge_document_id", "chunk_index"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // thứ tự đoạn trong tài liệu gốc, cố định sau khi tách
    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;

    // null cho tới khi tài liệu được index xong; sẽ đổi sang kiểu vector khi dùng pgvector
    private float[] embedding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_document_id", nullable = false)
    private KnowledgeDocument knowledgeDocument;
}
