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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    // Nhóm kỹ năng ít được phát triển nhất (điểm thấp nhất; bằng điểm thì lấy skill id nhỏ hơn).
    // Rỗng khi bé chưa có sản phẩm nào: mọi nhóm đều 0 nên chưa có cơ sở để so sánh.
    public Optional<SkillScore> getWeakestSkill() {
        if (totalProducts == 0) {
            return Optional.empty();
        }
        return skillScores.stream()
                .min(Comparator.comparingDouble(SkillScore::getScore)
                        .thenComparing(score -> score.getSkill().getId()));
    }

    // Nhóm kỹ năng đang được chú trọng nhất; rỗng khi chưa nhóm nào có điểm.
    public Optional<SkillScore> getStrongestSkill() {
        return skillScores.stream()
                .filter(score -> score.getScore() > 0)
                .max(Comparator.comparingDouble(SkillScore::getScore)
                        .thenComparing(score -> score.getSkill().getId(), Comparator.reverseOrder()));
    }
}
