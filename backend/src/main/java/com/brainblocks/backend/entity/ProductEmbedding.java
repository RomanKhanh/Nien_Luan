package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Vector ngữ nghĩa của một sản phẩm cho chatbot tìm sản phẩm theo ý nghĩa câu hỏi (ProductEmbeddingService).
 * Bảng phụ trợ, không có trên sơ đồ lớp. content_hash là SHA-256 của văn bản đã embed: chỉ gọi Gemini lại khi
 * văn bản (tên, danh mục, mô tả, độ tuổi, chỉ số kỹ năng) thay đổi. Xóa sản phẩm thì DB xóa theo (ON DELETE CASCADE).
 */
@Entity
@Table(
        name = "product_embeddings",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_embeddings_product", columnNames = "product_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_embeddings_product"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    // vector(768) của pgvector, cùng không gian với vector câu hỏi (GeminiService.EMBEDDING_SPACE)
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
