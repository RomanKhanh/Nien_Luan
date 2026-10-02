package com.brainblocks.backend.service.skill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Điểm một nhóm kỹ năng của bé theo kiểu lợi ích giảm dần, thang 0-10:
 * mỗi món tăng thêm productScore / 10 x (10 - điểm hiện tại) x alpha.
 * Dạng tính lại từ đầu, không phụ thuộc thứ tự mua: score = 10 x (1 - tích(1 - alpha x p / 10)).
 * Mua thêm món điểm thấp không bao giờ làm tụt điểm, và điểm tiến dần về 10 chứ không vượt.
 */
@Component
public class SkillScoreCalculator {
    public static final int MAX_SCORE = 10;

    private final double alpha;

    public SkillScoreCalculator(@Value("${app.skill-score.alpha:0.5}") double alpha) {
        // alpha > 1 thì một món 10 điểm làm (1 - alpha) âm, điểm vượt 10
        if (!(alpha > 0 && alpha <= 1)) {
            throw new IllegalStateException("app.skill-score.alpha must be in (0, 1], got " + alpha);
        }
        this.alpha = alpha;
    }

    // impactIndex của từng sản phẩm bé có vào nhóm kỹ năng này; làm tròn 1 chữ số
    public double score(Collection<Integer> impacts) {
        double remaining = 1;
        for (int impact : impacts) {
            // CatalogSeeder ghi thẳng vào DB, không qua validate 0-10 của SkillImpactRequest
            int clamped = Math.clamp(impact, 0, MAX_SCORE);
            remaining *= 1 - alpha * clamped / MAX_SCORE;
        }
        return BigDecimal.valueOf(MAX_SCORE * (1 - remaining))
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
