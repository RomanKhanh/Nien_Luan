package com.brainblocks.backend.service.skill;

/**
 * Mức của điểm hồ sơ kỹ năng (0-10). Ngưỡng phải khớp skillLevel() ở frontend (src/lib/skills.ts):
 * 0 = Chưa có, dưới 3,5 = Mới bắt đầu, dưới 7 = Đang phát triển, từ 7 = Phong phú.
 */
public enum SkillLevel {
    NONE("Chưa có"),
    BEGINNER("Mới bắt đầu"),
    DEVELOPING("Đang phát triển"),
    RICH("Phong phú");

    private final String label;

    SkillLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SkillLevel of(double score) {
        if (score <= 0) {
            return NONE;
        }
        if (score < 3.5) {
            return BEGINNER;
        }
        return score < 7 ? DEVELOPING : RICH;
    }
}
