package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.Role;
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

    // địa chỉ mặc định dạng chuỗi tự do của tài khoản cũ; chọn lại theo 3 cấp bên dưới thì bị xóa
    @Column(length = 255)
    private String defaultAddress;

    // địa chỉ mặc định 3 cấp (mã theo LocationDirectory), tự điền ở trang thanh toán; tên lấy từ dữ liệu địa chính
    private Integer defaultProvinceCode;

    private Integer defaultDistrictCode;

    // null khi huyện không có cấp xã
    private Integer defaultWardCode;

    @Column(length = 255)
    private String defaultAddressDetail;

    // Giỏ hàng (quan hệ 1-1 thành phần) chỉ map ở phía Cart.customer, không map ngược ở đây:
    // phía mappedBy của @OneToOne không lazy được, mỗi lần load Customer (kể cả lúc lọc JWT) sẽ tốn thêm 1 query.
    // Giỏ được tạo cùng tài khoản ở AuthService.register và lấy qua CartRepository.

    // thành phần: hồ sơ trẻ không tồn tại tách rời khách hàng
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

    @Override
    public Role getRole() {
        return Role.CUSTOMER;
    }
}
