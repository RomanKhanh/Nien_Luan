package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "skill_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_scores_profile_skill",
                columnNames = {"skill_profile_id", "skill_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private double percentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_profile_id", nullable = false)
    private SkillProfile skillProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;
}
