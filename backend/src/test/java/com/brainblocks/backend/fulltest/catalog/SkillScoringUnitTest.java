package com.brainblocks.backend.fulltest.catalog;

import com.brainblocks.backend.dto.response.skill.SkillGainResponse;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildProfileRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductSkillImpactRepository;
import com.brainblocks.backend.repository.SkillProfileRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import com.brainblocks.backend.service.skill.SkillProfileService;
import com.brainblocks.backend.service.skill.SkillScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SkillScoringUnitTest {
    @Mock SkillProfileRepository profiles;
    @Mock SkillRepository skills;
    @Mock ChildProductRepository assignments;
    @Mock ChildProfileRepository children;
    @Mock ProductRepository products;
    @Mock ProductSkillImpactRepository impacts;
    @Mock ChildProfileAccessGuard access;
    private final SkillScoreCalculator calculator = new SkillScoreCalculator(0.5);
    private SkillProfileService service;

    @BeforeEach
    void setUp() {
        service = new SkillProfileService(profiles, skills, assignments, children, products, impacts, access, calculator);
    }

    @Test
    void scoresKnownSingleAndMultipleProductExamples() {
        assertThat(calculator.score(List.of())).isZero();
        assertThat(calculator.score(List.of(8))).isEqualTo(4);
        assertThat(calculator.score(List.of(8, 2))).isEqualTo(4.6);
        assertThat(calculator.score(List.of(10, 2, 6))).isEqualTo(6.9);
    }

    @Test
    void scoresAreOrderIndependentAndRemainWithinScale() {
        assertThat(calculator.score(List.of(2, 8, 6))).isEqualTo(calculator.score(List.of(6, 2, 8)));
        assertThat(calculator.score(Collections.nCopies(100, 10))).isEqualTo(10);
        assertThat(calculator.score(List.of(-1, 11))).isEqualTo(5);
        assertThat(calculator.score(List.of(8, 0))).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidAlpha(double alpha) {
        assertThatThrownBy(() -> new SkillScoreCalculator(alpha)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void projectedGainsUseDiminishingReturnsAndDoNotMutateCurrentImpacts() {
        List<Integer> original = new ArrayList<>(List.of(10));
        Map<Long, List<Integer>> current = Map.of(1L, original);
        List<SkillGainResponse> result = service.projectGains(current,
                List.of(impact(2, "STEM", 8), impact(1, "LOGIC", 2), impact(3, "CREATIVE", 0)));
        assertThat(result).extracting(SkillGainResponse::skillCode).containsExactly("LOGIC", "STEM");
        assertThat(result.get(0).currentScore()).isEqualTo(5);
        assertThat(result.get(0).projectedScore()).isEqualTo(5.5);
        assertThat(result.get(0).gain()).isEqualTo(0.5);
        assertThat(result.get(1).currentScore()).isZero();
        assertThat(result.get(1).projectedScore()).isEqualTo(4);
        assertThat(result.get(1).gain()).isEqualTo(4);
        assertThat(original).containsExactly(10);
        verifyNoInteractions(profiles, skills, assignments, children, products, impacts, access);
    }

    @Test
    void emptyCandidateImpactsHaveNoProjectedGains() {
        assertThat(service.projectGains(Map.of(), List.<ProductSkillImpact>of())).isEmpty();
    }

    private ProductSkillImpact impact(long id, String code, int value) {
        return ProductSkillImpact.builder().skill(Skill.builder().id(id).code(code).name(code).build())
                .impactIndex(value).build();
    }
}
