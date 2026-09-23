package com.brainblocks.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_skill_impacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkillImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int impactIndex;

    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
