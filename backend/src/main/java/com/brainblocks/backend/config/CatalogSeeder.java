package com.brainblocks.backend.config;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lúc khởi động:
 * 1. luôn bảo đảm có 4 nhóm kỹ năng gốc của đề (Logic, Sáng tạo, Giải quyết vấn đề, STEM), thiếu nhóm nào thì thêm;
 * 2. nếu bật app.seed.demo-data và chưa có sản phẩm nào: tạo danh mục + sản phẩm mẫu để chạy thử giao diện.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogSeeder implements CommandLineRunner {
    private static final List<String[]> BASE_SKILLS = List.of(
            new String[]{"LOGIC", "Tư duy Logic", "Suy luận, sắp xếp theo quy tắc, nhận biết quy luật"},
            new String[]{"CREATIVE", "Sáng tạo", "Tự thiết kế, tưởng tượng, thể hiện ý tưởng riêng"},
            new String[]{"PROBLEM_SOLVING", "Giải quyết vấn đề", "Thử - sai, tìm cách vượt qua thử thách mở"},
            new String[]{"STEM", "STEM", "Khoa học, công nghệ, kỹ thuật và toán học"}
    );

    private final SkillRepository skillRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        for (String[] s : BASE_SKILLS) {
            if (!skillRepository.existsByCode(s[0])) {
                skillRepository.save(Skill.builder().code(s[0]).name(s[1]).description(s[2]).build());
                log.info("Seeded skill {}", s[0]);
            }
        }
        if (seedDemoData && productRepository.count() == 0) {
            seedDemoCatalog();
        }
    }

    private void seedDemoCatalog() {
        Map<String, Skill> skills = new HashMap<>();
        BASE_SKILLS.forEach(s -> skills.put(s[0], skillRepository.findByCode(s[0]).orElseThrow()));
        Map<String, Category> categories = new HashMap<>();
        for (String[] c : List.of(
                new String[]{"Bộ lắp ráp", "Lắp ghép mô hình, khối xây dựng"},
                new String[]{"Robot & lập trình", "Robot, mạch điện, lập trình không màn hình"},
                new String[]{"Xếp hình & câu đố", "Puzzle, khối gỗ, câu đố logic"},
                new String[]{"Khoa học thí nghiệm", "Bộ thí nghiệm khoa học tại nhà"},
                new String[]{"Mỹ thuật sáng tạo", "Vẽ, nặn, thủ công"},
                new String[]{"Board game tư duy", "Trò chơi bàn cờ rèn chiến thuật"})) {
            Category category = categoryRepository.findAll().stream()
                    .filter(existing -> existing.getName().equalsIgnoreCase(c[0]))
                    .findFirst()
                    .orElseGet(() -> categoryRepository.save(Category.builder().name(c[0]).description(c[1]).build()));
            categories.put(c[0], category);
        }
        // tên, danh mục, giá, tồn, tuổi min, tuổi max, Logic, Sáng tạo, GQVĐ, STEM,
        // cân nặng sau đóng gói (g), dài, rộng, cao của hộp (cm), mô tả
        Object[][] products = {
                {"Bộ lắp ráp robot dò đường 42 chi tiết", "Robot & lập trình", 459000, 24, 6, 8, 8, 5, 7, 9, 650, 30, 22, 8,
                        "Bé tự lắp một chiếc robot có cảm biến dò theo vạch kẻ, học nguyên lý cảm biến quang và cách nối mạch cơ bản."},
                {"Xếp hình logic khối gỗ 120 chi tiết", "Xếp hình & câu đố", 320000, 40, 4, 8, 9, 4, 9, 3, 1400, 32, 24, 9,
                        "120 khối gỗ nhiều hình dạng kèm 60 thẻ thử thách từ dễ đến khó."},
                {"Bộ mạch điện sáng tạo 30 mô-đun", "Robot & lập trình", 689000, 15, 7, 12, 6, 8, 7, 9, 1100, 35, 25, 7,
                        "Mạch điện dạng khối nam châm, bé tự thiết kế đèn, còi, quạt theo ý mình."},
                {"Bộ thử thách cơ khí bánh răng", "Bộ lắp ráp", 375000, 30, 6, 10, 7, 5, 9, 7, 800, 30, 22, 8,
                        "24 thử thách mở với bánh răng, trục và tay quay."},
                {"Robot lập trình không màn hình Cubetto", "Robot & lập trình", 1250000, 8, 3, 6, 8, 4, 7, 8, 2200, 40, 30, 12,
                        "Lập trình bằng khối gỗ, không cần màn hình, phù hợp trẻ mầm non."},
                {"Bộ thí nghiệm núi lửa và tinh thể", "Khoa học thí nghiệm", 289000, 50, 6, 12, 4, 5, 6, 9, 900, 32, 24, 8,
                        "12 thí nghiệm hóa học an toàn tại nhà, có hướng dẫn tiếng Việt."},
                {"Kính hiển vi trẻ em 1200x", "Khoa học thí nghiệm", 890000, 12, 8, 14, 4, 3, 5, 10, 1600, 36, 24, 14,
                        "Quan sát tế bào, lá cây, côn trùng; kèm 10 tiêu bản mẫu."},
                {"Bộ đất nặn an toàn 24 màu", "Mỹ thuật sáng tạo", 199000, 80, 3, 8, 1, 10, 3, 1, 1300, 30, 20, 10,
                        "Đất nặn từ bột mì, không dính tay, kèm khuôn và dụng cụ."},
                {"Bảng vẽ nam châm và bộ tem hình", "Mỹ thuật sáng tạo", 159000, 60, 2, 5, 1, 9, 2, 0, 600, 34, 26, 5,
                        "Vẽ và xóa không bụi, kèm tem hình khối để bé sáng tác."},
                {"Board game Rô-bốt tìm đường", "Board game tư duy", 345000, 25, 5, 10, 9, 3, 8, 5, 900, 30, 30, 7,
                        "Trò chơi lập kế hoạch đường đi, rèn tư duy thuật toán."},
                {"Cờ tư duy 4 trong 1", "Board game tư duy", 240000, 35, 6, 12, 8, 2, 7, 2, 1000, 36, 36, 5,
                        "Cờ vua, cờ caro, cờ cá ngựa, cờ tướng trong một hộp."},
                {"Khối nam châm xây dựng 64 chi tiết", "Bộ lắp ráp", 520000, 20, 3, 8, 5, 9, 6, 6, 1800, 34, 26, 10,
                        "Khối nam châm hình học để xây nhà, xe, tháp theo trí tưởng tượng."},
                {"Bộ lắp ráp xe năng lượng mặt trời 12 in 1", "Bộ lắp ráp", 410000, 3, 8, 14, 6, 6, 7, 9, 850, 32, 24, 8,
                        "12 mô hình chạy bằng pin mặt trời, học về năng lượng tái tạo."},
                {"Puzzle bản đồ Việt Nam 63 mảnh", "Xếp hình & câu đố", 180000, 45, 5, 10, 6, 2, 5, 4, 500, 30, 24, 4,
                        "Ghép bản đồ các tỉnh thành, kèm thẻ kiến thức địa lý."},
                {"Bộ thủ công tự làm ô tô bìa cứng", "Mỹ thuật sáng tạo", 135000, 0, 5, 9, 3, 9, 6, 4, 350, 30, 22, 5,
                        "Tự cắt, dán, trang trí ô tô bìa cứng chạy bằng dây thun."},
                {"Bộ cảm biến lập trình Micro:bit cho bé", "Robot & lập trình", 980000, 10, 9, 15, 8, 7, 8, 10, 700, 26, 20, 8,
                        "Lập trình kéo thả với cảm biến nhiệt, ánh sáng, chuyển động."},
        };
        for (Object[] p : products) {
            Product product = Product.builder()
                    .name((String) p[0])
                    .category(categories.get((String) p[1]))
                    .price(BigDecimal.valueOf((Integer) p[2]))
                    .stockQuantity((Integer) p[3])
                    .minAge((Integer) p[4])
                    .maxAge((Integer) p[5])
                    .weightGrams((Integer) p[10])
                    .lengthCm((Integer) p[11])
                    .widthCm((Integer) p[12])
                    .heightCm((Integer) p[13])
                    .description((String) p[14])
                    .build();
            String[] codes = {"LOGIC", "CREATIVE", "PROBLEM_SOLVING", "STEM"};
            for (int i = 0; i < codes.length; i++) {
                int value = (Integer) p[6 + i];
                if (value > 0) {
                    product.getProductSkillImpacts().add(ProductSkillImpact.builder()
                            .product(product).skill(skills.get(codes[i])).impactIndex(value).build());
                }
            }
            productRepository.save(product);
        }
        log.info("Seeded {} demo products", products.length);
    }
}
