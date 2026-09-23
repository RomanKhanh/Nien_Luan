package com.brainblocks.backend.entity;

import com.brainblocks.backend.enums.SkillCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "skills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    @Id
    @Enumerated(EnumType.STRING)
    private SkillCode code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Thứ tự hiển thị trên biểu đồ kỹ năng
    @Column(nullable = false)
    private Integer displayOrder;

    // Cho phép ẩn kỹ năng thay vì xóa cứng (vì đã gắn với sản phẩm)
    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductSkillImpact> productSkillImpacts = new ArrayList<>();

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SkillScore> skillScores = new ArrayList<>();
}
