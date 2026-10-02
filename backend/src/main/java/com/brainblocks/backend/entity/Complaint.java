package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // null khi chưa admin nào nhận xử lý
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private Admin handledBy;

    // các dòng hàng cụ thể bị khiếu nại kèm số lượng lỗi; rỗng = khiếu nại chung cả đơn.
    // thành phần: xóa complaint thì xóa luôn các dòng ComplaintItem
    @Builder.Default
    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComplaintItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplaintStatus status;

    @Column(columnDefinition = "TEXT")
    private String response;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime handledAt;

    // lúc admin tiếp nhận (PROCESSING); yêu cầu trả hàng tính hạn gửi hàng về từ mốc này (ReturnPolicy)
    private LocalDateTime acceptedAt;

    // video mở hàng, ảnh / video tình trạng sản phẩm; batch để trang danh sách admin không N+1
    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComplaintAttachment> attachments = new ArrayList<>();

    // true = admin giữ lại bằng chứng (vd đang tranh chấp, làm việc với đơn vị vận chuyển): không tự xóa file
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean evidenceHold = false;

    // true = yêu cầu đổi / trả hàng bị hệ thống tự từ chối vì khách gửi hàng về trễ hạn
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean returnExpired = false;
}