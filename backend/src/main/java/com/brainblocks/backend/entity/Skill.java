package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 4 nhóm ban đầu: LOGIC, CREATIVE, PROBLEM_SOLVING, STEM - lưu thành dữ liệu để mở rộng
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // hai lớp kết hợp dưới đây được cascade từ phía Product và SkillProfile
    @Builder.Default
    @OneToMany(mappedBy = "skill")
    private List<ProductSkillImpact> productSkillImpacts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "skill")
    private List<SkillScore> skillScores = new ArrayList<>();
}
