package com.brainblocks.backend.service.ai;

import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Văn bản đại diện cho một sản phẩm khi embed (RETRIEVAL_DOCUMENT): tên, danh mục, độ tuổi, mô tả và chỉ số tác
 * động kỹ năng diễn đạt thành câu, để câu hỏi như "đồ chơi giúp bé kiên nhẫn" khớp được theo ý nghĩa dù
 * không trùng chữ nào trong tên sản phẩm.
 */
public final class ProductEmbeddingText {
    static final int MAX_DESCRIPTION = 2000;

    private ProductEmbeddingText() {}

    // skills: mọi nhóm kỹ năng của hệ thống, để nói rõ cả nhóm sản phẩm ít tác động
    public static String build(Product product, List<Skill> skills) {
        StringBuilder text = new StringBuilder();
        text.append("Sản phẩm: ").append(product.getName().strip()).append(".\n");
        if (product.getCategory() != null) text.append("Danh mục: ").append(product.getCategory().getName().strip()).append(".\n");
        text.append("Độ tuổi phù hợp: từ ").append(product.getMinAge()).append(" đến ").append(product.getMaxAge()).append(" tuổi.\n");
        String description = product.getDescription() == null ? "" : product.getDescription().strip();
        if (!description.isEmpty()) {
            text.append("Mô tả: ").append(description, 0, Math.min(description.length(), MAX_DESCRIPTION)).append('\n');
        }
        List<ProductSkillImpact> impacts = product.getProductSkillImpacts().stream()
                .filter(i -> i.getImpactIndex() > 0)
                .sorted(Comparator.comparingInt(ProductSkillImpact::getImpactIndex).reversed()
                        .thenComparing(i -> i.getSkill().getId()))
                .toList();
        if (!impacts.isEmpty()) {
            text.append("Tác động đến kỹ năng của trẻ (thang 0-10):\n");
            for (ProductSkillImpact impact : impacts) {
                text.append("- ").append(impact.getSkill().getName().strip()).append(": ").append(impact.getImpactIndex())
                        .append("/10, ").append(level(impact.getImpactIndex())).append(".\n");
            }
        }
        Set<Long> covered = impacts.stream().map(i -> i.getSkill().getId()).collect(Collectors.toSet());
        String weak = skills.stream().filter(s -> !covered.contains(s.getId()))
                .sorted(Comparator.comparing(Skill::getId)).map(s -> s.getName().strip()).collect(Collectors.joining(", "));
        if (!weak.isEmpty()) text.append("Ít tác động đến: ").append(weak).append(".\n");
        return text.toString().strip();
    }

    static String level(int impactIndex) {
        if (impactIndex >= 7) return "tác động mạnh";
        if (impactIndex >= 4) return "tác động vừa";
        return "tác động nhẹ";
    }

    // Đổi model / số chiều embedding cũng làm hash đổi, nên vector cũ được embed lại.
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((GeminiService.EMBEDDING_SPACE + '\n' + text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
