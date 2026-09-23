package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    private int displayOrder;

    private boolean isThumbnail;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
