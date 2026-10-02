package com.brainblocks.backend.service.skill;

import com.brainblocks.backend.dto.response.skill.SkillGainResponse;
import com.brainblocks.backend.dto.response.skill.SkillPreviewResponse;
import com.brainblocks.backend.dto.response.skill.SkillProfileResponse;
import com.brainblocks.backend.dto.response.skill.SkillScoreResponse;
import com.brainblocks.backend.dto.response.skill.SkillTimelinePointResponse;
import com.brainblocks.backend.dto.response.skill.SkillTimelineResponse;
import com.brainblocks.backend.entity.ChildProduct;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.entity.SkillProfile;
import com.brainblocks.backend.entity.SkillScore;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildProductRepository.ChildProductCount;
import com.brainblocks.backend.repository.ChildProductRepository.ChildSkillImpact;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProfileRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillProfileRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.child.ChildProfileAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillProfileService {
    // số hồ sơ trẻ mỗi lô khi tính lại toàn bộ, để câu IN không quá dài
    private static final int RECALCULATE_BATCH_SIZE = 500;

    private final SkillProfileRepository skillProfileRepository;
    private final SkillRepository skillRepository;
    private final ChildProductRepository childProductRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ProductRepository productRepository;
    private final ChildProfileAccessGuard accessGuard;
    private final SkillScoreCalculator scoreCalculator;

    // không readOnly: hồ sơ trẻ tạo trước khi có tính năng này có thể chưa có SkillProfile, khi đó tạo luôn
    @Transactional
    public SkillProfileResponse getSkillProfile(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        return skillProfileRepository.findWithScoresByChildProfileId(child.getId())
                .map(this::toResponse)
                .orElseGet(() -> recalculateFor(child));
    }

    @Transactional
    public SkillProfileResponse recalculate(Long childId) {
        return recalculateFor(accessGuard.getOwnedChildProfile(childId));
    }

    /**
     * Tính lại hồ sơ kỹ năng cho một hồ sơ trẻ ĐÃ được kiểm tra quyền sở hữu.
     * Dùng nội bộ: ChildProfileService gọi sau khi thêm/gỡ sản phẩm; mảng đơn hàng gọi sau khi
     * tạo ChildProduct PURCHASED. Không nhận id từ client.
     * Số query cố định, không phụ thuộc số sản phẩm hay số skill.
     */
    @Transactional
    public SkillProfileResponse recalculateFor(ChildProfile child) {
        Long childId = child.getId();

        // đẩy thay đổi ChildProduct vừa thêm/gỡ xuống DB trước khi tổng hợp
        childProductRepository.flush();

        List<Skill> skills = skillRepository.findAll(Sort.by("id"));
        Map<Long, List<Integer>> impactsBySkillId = groupImpacts(List.of(childId))
                .getOrDefault(childId, Map.of());
        int totalProducts = (int) childProductRepository.countByChildProfileId(childId);

        SkillProfile profile = skillProfileRepository.findWithScoresByChildProfileId(childId)
                .orElseGet(() -> newProfile(child));

        applyScores(profile, skills, impactsBySkillId, totalProducts);
        return toResponse(skillProfileRepository.save(profile));
    }

    /**
     * Tính lại hồ sơ kỹ năng của MỌI trẻ đang sở hữu một sản phẩm.
     * Dành cho mảng sản phẩm gọi sau khi admin sửa impactIndex (ProductSkillImpact) của sản phẩm đó.
     * Chạy theo lô: số query cố định dù có bao nhiêu trẻ, không lặp gọi recalculateFor từng trẻ.
     *
     * @return số hồ sơ kỹ năng đã được tính lại
     */
    @Transactional
    public int recalculateForProduct(Long productId) {
        // đẩy thay đổi impactIndex đang chờ xuống DB trước khi tổng hợp
        childProductRepository.flush();
        return recalculateForChildren(childProductRepository.findChildProfileIdsByProductId(productId));
    }

    /**
     * Tính lại hồ sơ kỹ năng của mọi trẻ. SkillProfileRecalculationRunner gọi lúc khởi động để điểm
     * đang lưu luôn khớp công thức và alpha hiện tại (dữ liệu tính theo công thức cũ, hoặc vừa đổi
     * app.skill-score.alpha).
     *
     * @return số hồ sơ kỹ năng đã được tính lại
     */
    @Transactional
    public int recalculateAll() {
        List<Long> childIds = childProfileRepository.findAllIds();
        int updated = 0;
        for (int from = 0; from < childIds.size(); from += RECALCULATE_BATCH_SIZE) {
            updated += recalculateForChildren(
                    childIds.subList(from, Math.min(from + RECALCULATE_BATCH_SIZE, childIds.size())));
        }
        return updated;
    }

    private int recalculateForChildren(List<Long> childIds) {
        if (childIds.isEmpty()) {
            return 0;
        }

        List<Skill> skills = skillRepository.findAll(Sort.by("id"));
        Map<Long, Map<Long, List<Integer>>> impactsByChild = groupImpacts(childIds);
        Map<Long, Long> productCountByChild = childProductRepository.countByChildProfileIds(childIds).stream()
                .collect(Collectors.toMap(ChildProductCount::getChildProfileId, ChildProductCount::getTotal));

        Map<Long, SkillProfile> profileByChild = skillProfileRepository.findAllWithScoresByChildProfileIdIn(childIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getChildProfile().getId(), Function.identity()));

        // trẻ chưa có SkillProfile (dữ liệu cũ) thì tạo mới, load các hồ sơ trẻ còn thiếu trong 1 câu
        List<Long> missingIds = childIds.stream().filter(id -> !profileByChild.containsKey(id)).toList();
        if (!missingIds.isEmpty()) {
            childProfileRepository.findAllById(missingIds)
                    .forEach(child -> profileByChild.put(child.getId(), newProfile(child)));
        }

        List<SkillProfile> updated = new ArrayList<>();
        for (Map.Entry<Long, SkillProfile> entry : profileByChild.entrySet()) {
            Long childId = entry.getKey();
            applyScores(entry.getValue(), skills,
                    impactsByChild.getOrDefault(childId, Map.of()),
                    productCountByChild.getOrDefault(childId, 0L).intValue());
            updated.add(entry.getValue());
        }
        skillProfileRepository.saveAll(updated);
        return updated.size();
    }

    // childId -> (skillId -> impactIndex của từng sản phẩm bé có vào skill đó), lấy trong 1 câu
    private Map<Long, Map<Long, List<Integer>>> groupImpacts(List<Long> childIds) {
        Map<Long, Map<Long, List<Integer>>> impactsByChild = new HashMap<>();
        for (ChildSkillImpact row : childProductRepository.findSkillImpactsForChildren(childIds)) {
            impactsByChild.computeIfAbsent(row.getChildProfileId(), id -> new HashMap<>())
                    .computeIfAbsent(row.getSkillId(), id -> new ArrayList<>())
                    .add(row.getImpactIndex());
        }
        return impactsByChild;
    }

    /**
     * Dự kiến điểm từng nhóm kỹ năng của bé nếu thêm sản phẩm này, để phụ huynh thấy món đó
     * bổ sung được bao nhiêu cho chính bé (nhóm đã cao thì tăng ít, nhóm còn thấp thì tăng nhiều).
     */
    @Transactional(readOnly = true)
    public SkillPreviewResponse previewProduct(Long childId, Long productId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        // sản phẩm đã ẩn coi như không tồn tại với phía khách hàng, giống ChildProfileService.assignProduct
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (childProductRepository.existsByChildProfileIdAndProductId(child.getId(), product.getId())) {
            return new SkillPreviewResponse(child.getId(), product.getId(), true, List.of());
        }
        return new SkillPreviewResponse(child.getId(), product.getId(), false,
                projectGains(currentImpacts(child.getId()), product));
    }

    // skillId -> impactIndex của từng sản phẩm bé đang có; childId phải đã được kiểm tra quyền sở hữu
    @Transactional(readOnly = true)
    public Map<Long, List<Integer>> currentImpacts(Long childId) {
        return groupImpacts(List.of(childId)).getOrDefault(childId, Map.of());
    }

    // điểm hiện tại và sau khi thêm sản phẩm của các nhóm mà sản phẩm có tác động, theo id skill
    public List<SkillGainResponse> projectGains(Map<Long, List<Integer>> impactsBySkillId, Product product) {
        return product.getProductSkillImpacts().stream()
                .filter(impact -> impact.getImpactIndex() > 0)
                .sorted(Comparator.comparing(impact -> impact.getSkill().getId()))
                .map(impact -> {
                    Skill skill = impact.getSkill();
                    List<Integer> current = impactsBySkillId.getOrDefault(skill.getId(), List.of());
                    List<Integer> projected = new ArrayList<>(current);
                    projected.add(impact.getImpactIndex());
                    double currentScore = scoreCalculator.score(current);
                    double projectedScore = scoreCalculator.score(projected);
                    // trừ trên BigDecimal để không ra kiểu 0.30000000000000004
                    double gain = BigDecimal.valueOf(projectedScore)
                            .subtract(BigDecimal.valueOf(currentScore))
                            .doubleValue();
                    return new SkillGainResponse(skill.getId(), skill.getCode(), skill.getName(),
                            currentScore, projectedScore, gain);
                })
                .toList();
    }

    private SkillProfile newProfile(ChildProfile child) {
        SkillProfile created = SkillProfile.builder().childProfile(child).build();
        child.setSkillProfile(created);
        return created;
    }

    // cập nhật SkillScore đang có thay vì xóa đi tạo lại; skill mới được thêm thì tạo SkillScore mới
    private void applyScores(SkillProfile profile, List<Skill> skills,
                             Map<Long, List<Integer>> impactsBySkillId, int totalProducts) {
        Map<Long, Double> scoreBySkillId = scoreSkills(skills, impactsBySkillId);
        double totalScore = sum(scoreBySkillId);

        Map<Long, SkillScore> skillScoreBySkillId = profile.getSkillScores().stream()
                .collect(Collectors.toMap(score -> score.getSkill().getId(), Function.identity()));

        for (Skill skill : skills) {
            double score = scoreBySkillId.get(skill.getId());
            SkillScore skillScore = skillScoreBySkillId.get(skill.getId());
            if (skillScore == null) {
                skillScore = SkillScore.builder().skillProfile(profile).skill(skill).build();
                profile.getSkillScores().add(skillScore);
            }
            skillScore.setScore(score);
            skillScore.setPercentage(toPercentage(score, totalScore));
        }

        profile.setTotalProducts(totalProducts);
        // @UpdateTimestamp chỉ chạy khi chính SkillProfile bị sửa; nếu chỉ SkillScore đổi
        // thì updatedAt đứng yên, nên gán tay để luôn phản ánh lần tính gần nhất
        profile.setUpdatedAt(LocalDateTime.now());
    }

    // skillId -> điểm thang 0-10 (SkillScoreCalculator); skill chưa có món nào tác động thì 0
    private Map<Long, Double> scoreSkills(List<Skill> skills, Map<Long, List<Integer>> impactsBySkillId) {
        Map<Long, Double> scoreBySkillId = new HashMap<>();
        for (Skill skill : skills) {
            scoreBySkillId.put(skill.getId(),
                    scoreCalculator.score(impactsBySkillId.getOrDefault(skill.getId(), List.of())));
        }
        return scoreBySkillId;
    }

    private double sum(Map<Long, Double> scoreBySkillId) {
        return scoreBySkillId.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    // phần của nhóm trong tổng điểm các nhóm: score / tổng * 100, làm tròn 1 chữ số; tổng = 0 thì trả 0
    private double toPercentage(double score, double total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(score)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Lộ trình kỹ năng: mỗi mốc là một lần bé có thêm sản phẩm (theo ChildProduct.addedAt),
     * điểm và phần trăm tính tới mốc đó theo cùng công thức với hồ sơ kỹ năng.
     * Dựng lại từ dữ liệu hiện có, không lưu lịch sử riêng,
     * nên sản phẩm đã gỡ sẽ không còn trên lộ trình và điểm dùng impactIndex hiện tại.
     */
    @Transactional(readOnly = true)
    public SkillTimelineResponse getTimeline(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        List<Skill> skills = skillRepository.findAll(Sort.by("id"));

        Map<Long, List<Integer>> cumulative = new HashMap<>();
        List<SkillTimelinePointResponse> points = new ArrayList<>();
        int count = 0;

        for (ChildProduct childProduct : childProductRepository.findTimelineByChildProfileId(child.getId())) {
            for (ProductSkillImpact impact : childProduct.getProduct().getProductSkillImpacts()) {
                cumulative.computeIfAbsent(impact.getSkill().getId(), id -> new ArrayList<>())
                        .add(impact.getImpactIndex());
            }
            count++;

            Map<Long, Double> scoreBySkillId = scoreSkills(skills, cumulative);
            double totalScore = sum(scoreBySkillId);
            List<SkillScoreResponse> scores = skills.stream()
                    .map(skill -> {
                        double score = scoreBySkillId.get(skill.getId());
                        return new SkillScoreResponse(skill.getId(), skill.getCode(), skill.getName(),
                                score, toPercentage(score, totalScore));
                    })
                    .toList();

            points.add(new SkillTimelinePointResponse(
                    childProduct.getId(),
                    childProduct.getProduct().getId(),
                    childProduct.getProduct().getName(),
                    childProduct.getSource().name(),
                    childProduct.getAddedAt(),
                    count,
                    scores
            ));
        }
        return new SkillTimelineResponse(child.getId(), points);
    }

    private SkillProfileResponse toResponse(SkillProfile profile) {
        List<SkillScoreResponse> scores = profile.getSkillScores().stream()
                .map(this::toScoreResponse)
                .sorted(Comparator.comparing(SkillScoreResponse::skillId))
                .toList();

        return new SkillProfileResponse(
                profile.getChildProfile().getId(),
                profile.getTotalProducts(),
                profile.getUpdatedAt(),
                scores,
                profile.getStrongestSkill().map(this::toScoreResponse).orElse(null),
                profile.getWeakestSkill().map(this::toScoreResponse).orElse(null)
        );
    }

    private SkillScoreResponse toScoreResponse(SkillScore score) {
        return new SkillScoreResponse(
                score.getSkill().getId(),
                score.getSkill().getCode(),
                score.getSkill().getName(),
                score.getScore(),
                score.getPercentage());
    }
}
