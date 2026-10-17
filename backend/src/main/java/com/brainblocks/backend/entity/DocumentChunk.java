package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    // vector(768) của pgvector, null cho tới khi tài liệu được index xong. Cột cũ kiểu real[] đổi bằng
    // docs/sql/pgvector-migration.sql; PgVectorSchemaCheck dừng app nếu cột chưa đúng kiểu
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    private float[] embedding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_document_id", nullable = false)
    private KnowledgeDocument knowledgeDocument;
}
