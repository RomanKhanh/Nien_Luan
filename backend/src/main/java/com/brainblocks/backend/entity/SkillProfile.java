package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "skill_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int totalProducts;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_profile_id", nullable = false, unique = true)
    private ChildProfile childProfile;

    // lớp kết hợp SkillProfile - Skill, cascade từ phía hồ sơ kỹ năng
    @Builder.Default
    @OneToMany(mappedBy = "skillProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillScore> skillScores = new ArrayList<>();
}
