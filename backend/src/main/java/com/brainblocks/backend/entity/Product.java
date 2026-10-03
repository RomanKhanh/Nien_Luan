package com.brainblocks.backend.entity;

import com.brainblocks.backend.util.SearchTextUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // link YouTube hướng dẫn sử dụng sản phẩm, không bắt buộc
    @Column(length = 500)
    private String videoUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private int minAge;

    @Column(nullable = false)
    private int maxAge;

    // Thông số vận chuyển của gói hàng SAU khi đóng gói, để tính phí ship sau này.
    // Gram và cm nguyên cho khỏi số lẻ. @ColumnDefault chỉ để ddl-auto=update thêm được cột NOT NULL
    // vào bảng đã có dữ liệu: sản phẩm cũ nhận 500 g, 20×15×10 cm; sản phẩm mới luôn phải nhập đủ.
    @ColumnDefault("500")
    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @ColumnDefault("20")
    @Column(name = "length_cm", nullable = false)
    private Integer lengthCm;

    @ColumnDefault("15")
    @Column(name = "width_cm", nullable = false)
    private Integer widthCm;

    @ColumnDefault("10")
    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    // false = ẩn sản phẩm thay vì xóa cứng, vì còn gắn với đơn hàng cũ
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // tên + mô tả đã bỏ dấu, chữ thường (SearchTextUtils) để tìm kiếm không dấu; tự cập nhật khi lưu.
    // Cột phụ trợ cho tìm kiếm, không có trên sơ đồ lớp
    @Column(columnDefinition = "TEXT")
    private String searchText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // thành phần: ảnh không tồn tại tách rời sản phẩm
    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> productImages = new ArrayList<>();

    // lớp kết hợp Product - Skill, cascade từ phía sản phẩm
    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSkillImpact> productSkillImpacts = new ArrayList<>();

    // dữ liệu lịch sử / thuộc sở hữu của phía khác: không cascade xóa theo sản phẩm
    @Builder.Default
    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product")
    private List<CartItem> cartItems = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product")
    private List<Review> reviews = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product")
    private List<ChildProduct> childProducts = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void syncSearchText() {
        this.searchText = SearchTextUtils.normalize(name + " " + (description == null ? "" : description));
    }
}
