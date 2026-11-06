package com.brainblocks.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Chỉ lưu định danh token đã đăng xuất, không ảnh hưởng các thiết bị khác.
@Entity
@Table(name = "revoked_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {
    @Id
    @Column(length = 36)
    private String jti;

    // Thời điểm hết hạn theo UTC; sau đó có thể xóa bản ghi thu hồi.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
