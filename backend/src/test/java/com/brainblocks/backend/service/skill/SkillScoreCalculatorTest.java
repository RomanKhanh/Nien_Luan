package com.brainblocks.backend.service.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Công thức điểm kỹ năng theo lợi ích giảm dần: score = 10 x (1 - tích(1 - alpha x p / 10)).
 * Unit test thuần, không cần Spring.
 */
class SkillScoreCalculatorTest {
    private final SkillScoreCalculator calculator = new SkillScoreCalculator(0.3);

    @Test
    void noProductsScoresZero() {
        assertThat(calculator.score(List.of())).isZero();
    }

    // một món: increase = p / 10 x (10 - 0) x alpha = alpha x p
    @Test
    void singleProductScoresAlphaTimesImpact() {
        assertThat(calculator.score(List.of(8))).isEqualTo(2.4);
        assertThat(calculator.score(List.of(10))).isEqualTo(3.0);
    }

    // khớp dạng cộng dồn: 8 -> 2.4, rồi thêm 6 -> 2.4 + 6/10 x (10 - 2.4) x 0.3 = 3.768
    @Test
    void matchesIncrementalFormula() {
        assertThat(calculator.score(List.of(8, 6))).isEqualTo(3.8);
    }

    @Test
    void orderDoesNotMatter() {
        assertThat(calculator.score(List.of(2, 9, 5))).isEqualTo(calculator.score(List.of(9, 5, 2)));
    }

    // lỗi của cách tính cũ (chia trung bình): mua thêm món điểm thấp thì điểm tụt
    @Test
    void addingLowImpactProductNeverLowersScore() {
        double before = calculator.score(List.of(9));
        assertThat(calculator.score(List.of(9, 1))).isGreaterThanOrEqualTo(before);
        assertThat(calculator.score(List.of(9, 0))).isEqualTo(before);
    }

    @Test
    void approachesButNeverExceedsTen() {
        List<Integer> many = java.util.Collections.nCopies(50, 10);
        assertThat(calculator.score(many)).isLessThanOrEqualTo(10).isGreaterThan(9.9);
        assertThat(new SkillScoreCalculator(1).score(List.of(10))).isEqualTo(10);
    }

    // CatalogSeeder ghi thẳng DB, giá trị ngoài 0-10 bị kẹp lại để điểm luôn trong thang 0-10
    @Test
    void clampsOutOfRangeImpacts() {
        assertThat(calculator.score(List.of(15))).isEqualTo(calculator.score(List.of(10)));
        assertThat(calculator.score(List.of(-3))).isZero();
    }

    @Test
    void rejectsAlphaOutsideZeroToOne() {
        assertThatThrownBy(() -> new SkillScoreCalculator(0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SkillScoreCalculator(1.5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SkillScoreCalculator(Double.NaN)).isInstanceOf(IllegalStateException.class);
    }
}
