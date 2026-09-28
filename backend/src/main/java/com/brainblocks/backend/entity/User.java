package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
// chiến lược kế thừa phải khai ở lớp gốc, Customer/Admin không khai lại
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false)
    private String password;

    // Cột role giữ theo sơ đồ lớp, nhưng giá trị luôn suy ra từ lớp con (xem getRole/syncRoleColumn):
    // không thể có Customer mang role ADMIN do code tạo sai.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // false = tài khoản bị admin khóa
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    // Tăng mỗi lần đổi mật khẩu. JWT mang giá trị lúc phát hành; khác giá trị hiện tại thì token bị từ chối,
    // nên mọi token phát ra trước khi đổi mật khẩu hết hiệu lực ngay.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int tokenVersion = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Vai trò do lớp con quyết định (Admin -> ADMIN, Customer -> CUSTOMER); phân quyền JWT dùng giá trị này
    public abstract Role getRole();

    // luôn ghi cột role khớp với lớp con, kể cả khi code set nhầm qua builder/setter
    @PrePersist
    @PreUpdate
    private void syncRoleColumn() {
        this.role = getRole();
    }
}
