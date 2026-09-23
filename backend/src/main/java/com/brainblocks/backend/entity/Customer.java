package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends User {

    @Column(length = 255)
    private String defaultAddress;

    // thành phần: giỏ hàng và hồ sơ trẻ không tồn tại tách rời khách hàng
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Cart cart;

    @Builder.Default
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChildProfile> childProfiles = new ArrayList<>();

    // dữ liệu lịch sử: không cascade xóa theo khách hàng
    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<Order> orders = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<Complaint> complaints = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<Review> reviews = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<ChatSession> chatSessions = new ArrayList<>();
}
