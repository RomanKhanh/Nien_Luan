package com.brainblocks.backend.service.skill;

import com.brainblocks.backend.dto.response.ProductRecommendationResponse;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.entity.SkillProfile;
import com.brainblocks.backend.entity.SkillScore;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductRepository.ProductThumbnail;
import com.brainblocks.backend.repository.SkillProfileRepository;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Đề xuất sản phẩm tiếp theo cho bé (SkillProfile.recommendNext trên sơ đồ lớp).
 *
 * Ứng viên: sản phẩm đang bán, còn hàng, hợp tuổi bé, bé chưa có.
 * Điểm = 2 x impactIndex vào nhóm kỹ năng yếu nhất + tổng impactIndex vào các nhóm phụ huynh quan tâm.
 * Nếu chưa có cơ sở nào (bé chưa có sản phẩm, phụ huynh chưa chọn nhóm quan tâm) thì điểm = tổng impactIndex.
 * Sản phẩm điểm 0 bị loại. Bằng điểm thì ưu tiên tổng tác động cao hơn, rồi giá thấp hơn.
 */
@Service
@RequiredArgsConstructor
public class SkillRecommendationService {
    private static final int WEAKEST_SKILL_WEIGHT = 2;
    private static final int MAX_LIMIT = 20;

    private final ProductRepository productRepository;
    private final SkillProfileRepository skillProfileRepository;
    private final ChildProfileAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<ProductRecommendationResponse> recommendNext(Long childId, int limit) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        int size = Math.clamp(limit, 1, MAX_LIMIT);

        Skill weakestSkill = skillProfileRepository.findWithScoresByChildProfileId(child.getId())
                .flatMap(SkillProfile::getWeakestSkill)
                .map(SkillScore::getSkill)
                .orElse(null);
        Set<Long> interestedSkillIds = child.getInterestedSkills().stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());

        List<Scored> ranked = productRepository.findRecommendationCandidates(child.getId(), child.getAge()).stream()
                .map(product -> score(product, weakestSkill, interestedSkillIds))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::totalImpact).reversed())
                        .thenComparing(scored -> scored.product().getPrice())
                        .thenComparing(scored -> scored.product().getId()))
                .limit(size)
                .toList();

        if (ranked.isEmpty()) {
            return List.of();
        }

        // ảnh đại diện của cả danh sách lấy trong 1 câu
        Map<Long, String> thumbnailByProductId = productRepository
                .findThumbnails(ranked.stream().map(scored -> scored.product().getId()).toList())
                .stream()
                .collect(Collectors.toMap(ProductThumbnail::getProductId, ProductThumbnail::getUrl, (a, b) -> a));

        return ranked.stream()
                .map(scored -> {
                    Product product = scored.product();
                    return new ProductRecommendationResponse(
                            product.getId(),
                            product.getName(),
                            product.getPrice(),
                            product.getMinAge(),
                            product.getMaxAge(),
                            thumbnailByProductId.get(product.getId()),
                            scored.score(),
                            scored.reasons());
                })
                .toList();
    }

    private Scored score(Product product, Skill weakestSkill, Set<Long> interestedSkillIds) {
        int weakestImpact = 0;
        int interestedImpact = 0;
        int totalImpact = 0;
        List<String> interestReasons = new ArrayList<>();

        for (ProductSkillImpact impact : product.getProductSkillImpacts()) {
            Skill skill = impact.getSkill();
            int value = impact.getImpactIndex();
            totalImpact += value;
            if (weakestSkill != null && skill.getId().equals(weakestSkill.getId())) {
                weakestImpact = value;
            }
            if (interestedSkillIds.contains(skill.getId()) && value > 0) {
                interestedImpact += value;
                interestReasons.add("Thuộc nhóm phụ huynh quan tâm: " + skill.getName() + " (" + value + "/10)");
            }
        }

        boolean hasBasis = weakestSkill != null || !interestedSkillIds.isEmpty();
        int score = hasBasis ? WEAKEST_SKILL_WEIGHT * weakestImpact + interestedImpact : totalImpact;

        // lý do đề xuất: phục vụ yêu cầu "giải thích lý do đề xuất" (đề 2.3)
        List<String> reasons = new ArrayList<>();
        if (weakestImpact > 0) {
            reasons.add("Tác động " + weakestImpact + "/10 vào nhóm " + weakestSkill.getName()
                    + " - nhóm bé đang ít được phát triển nhất");
        }
        reasons.addAll(interestReasons);
        if (!hasBasis) {
            reasons.add("Tổng chỉ số tác động kỹ năng cao (" + totalImpact + ")");
        }
        reasons.add("Phù hợp độ tuổi " + product.getMinAge() + "-" + product.getMaxAge());

        return new Scored(product, score, totalImpact, reasons);
    }

    private record Scored(Product product, int score, int totalImpact, List<String> reasons) {
    }
}
