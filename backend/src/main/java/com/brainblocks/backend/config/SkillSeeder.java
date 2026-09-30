package com.brainblocks.backend.config;

import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// Tự tạo 4 nhóm kỹ năng ban đầu lúc khởi động nếu chưa có.
// Chỉ thêm skill còn thiếu theo code, không ghi đè name/description mà admin đã sửa.
// Thứ tự trong SEEDS quyết định id khi DB còn trống; id nhỏ hơn thắng khi bằng điểm ở getWeakestSkill.
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillSeeder implements CommandLineRunner {
    private record SkillSeed(String code, String name, String description) {
    }

    private static final List<SkillSeed> SEEDS = List.of(
            new SkillSeed("LOGIC", "Tư duy logic",
                    "Phát triển khả năng suy luận, tư duy tuần tự và nhận biết quy luật"),
            new SkillSeed("CREATIVE", "Sáng tạo",
                    "Khuyến khích trí tưởng tượng, tự do thiết kế và tạo ra ý tưởng mới"),
            new SkillSeed("PROBLEM_SOLVING", "Giải quyết vấn đề",
                    "Rèn luyện cách phân tích tình huống, thử nghiệm và tìm hướng giải quyết"),
            new SkillSeed("STEM", "Kiến thức STEM",
                    "Làm quen các khái niệm khoa học, công nghệ, kỹ thuật và toán học")
    );

    private final SkillRepository skillRepository;

    @Override
    public void run(String... args) {
        List<Skill> missing = SEEDS.stream()
                .filter(seed -> skillRepository.findByCodeIgnoreCase(seed.code()).isEmpty())
                .map(seed -> Skill.builder()
                        .code(seed.code())
                        .name(seed.name())
                        .description(seed.description())
                        .build())
                .toList();

        if (missing.isEmpty()) {
            return;
        }
        skillRepository.saveAll(missing);
        log.info("Seeded {} skill(s)", missing.size());
    }
}
