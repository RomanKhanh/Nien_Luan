package com.brainblocks.backend.config;

import com.brainblocks.backend.service.skill.SkillProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Tính lại hồ sơ kỹ năng của mọi bé lúc khởi động: điểm lưu trong skill_scores phải khớp công thức
// và app.skill-score.alpha hiện tại (dữ liệu cũ tính theo tổng impactIndex, hoặc alpha vừa được đổi)
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillProfileRecalculationRunner implements CommandLineRunner {
    private final SkillProfileService skillProfileService;

    @Override
    public void run(String... args) {
        int updated = skillProfileService.recalculateAll();
        if (updated > 0) {
            log.info("Recalculated {} skill profiles", updated);
        }
    }
}
