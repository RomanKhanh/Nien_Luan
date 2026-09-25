package com.brainblocks.backend.service.skill;

import com.brainblocks.backend.dto.response.SkillProfileResponse;
import com.brainblocks.backend.dto.response.SkillScoreResponse;
import com.brainblocks.backend.dto.response.SkillTimelinePointResponse;
import com.brainblocks.backend.dto.response.SkillTimelineResponse;
import com.brainblocks.backend.entity.ChildProduct;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.ProductSkillImpact;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.entity.SkillProfile;
import com.brainblocks.backend.entity.SkillScore;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildProductRepository.ChildProductCount;
import com.brainblocks.backend.repository.ChildProductRepository.ChildSkillImpactSum;
import com.brainblocks.backend.repository.ChildProductRepository.SkillImpactSum;
import com.brainblocks.backend.repository.ChildProfileRepository;
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
    private final SkillProfileRepository skillProfileRepository;
    private final SkillRepository skillRepository;
    private final ChildProductRepository childProductRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ChildProfileAccessGuard accessGuard;

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
        Map<Long, Long> impactBySkillId = childProductRepository.sumImpactBySkill(childId).stream()
                .collect(Collectors.toMap(SkillImpactSum::getSkillId, SkillImpactSum::getTotalImpact));
        int totalProducts = (int) childProductRepository.countByChildProfileId(childId);

        SkillProfile profile = skillProfileRepository.findWithScoresByChildProfileId(childId)
                .orElseGet(() -> newProfile(child));

        applyScores(profile, skills, impactBySkillId, totalProducts);
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

        List<Long> childIds = childProductRepository.findChildProfileIdsByProductId(productId);
        if (childIds.isEmpty()) {
            return 0;
        }

        List<Skill> skills = skillRepository.findAll(Sort.by("id"));

        // childId -> (skillId -> tổng impactIndex)
        Map<Long, Map<Long, Long>> impactByChild = new HashMap<>();
        for (ChildSkillImpactSum row : childProductRepository.sumImpactBySkillForChildren(childIds)) {
            impactByChild.computeIfAbsent(row.getChildProfileId(), id -> new HashMap<>())
                    .put(row.getSkillId(), row.getTotalImpact());
        }
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
                    impactByChild.getOrDefault(childId, Map.of()),
                    productCountByChild.getOrDefault(childId, 0L).intValue());
            updated.add(entry.getValue());
        }
        skillProfileRepository.saveAll(updated);
        return updated.size();
    }

    private SkillProfile newProfile(ChildProfile child) {
        SkillProfile created = SkillProfile.builder().childProfile(child).build();
        child.setSkillProfile(created);
        return created;
    }

    // cập nhật SkillScore đang có thay vì xóa đi tạo lại; skill mới được thêm thì tạo SkillScore mới
    private void applyScores(SkillProfile profile, List<Skill> skills,
                             Map<Long, Long> impactBySkillId, int totalProducts) {
        long totalImpact = impactBySkillId.values().stream().mapToLong(Long::longValue).sum();

        Map<Long, SkillScore> scoreBySkillId = profile.getSkillScores().stream()
                .collect(Collectors.toMap(score -> score.getSkill().getId(), Function.identity()));

        for (Skill skill : skills) {
            long score = impactBySkillId.getOrDefault(skill.getId(), 0L);
            SkillScore skillScore = scoreBySkillId.get(skill.getId());
            if (skillScore == null) {
                skillScore = SkillScore.builder().skillProfile(profile).skill(skill).build();
                profile.getSkillScores().add(skillScore);
            }
            skillScore.setScore(score);
            skillScore.setPercentage(toPercentage(score, totalImpact));
        }

        profile.setTotalProducts(totalProducts);
        // @UpdateTimestamp chỉ chạy khi chính SkillProfile bị sửa; nếu chỉ SkillScore đổi
        // thì updatedAt đứng yên, nên gán tay để luôn phản ánh lần tính gần nhất
        profile.setUpdatedAt(LocalDateTime.now());
    }

    // score / tổng * 100, làm tròn 1 chữ số; tổng = 0 thì trả 0 để không chia cho 0
    private double toPercentage(long score, long total) {
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
     * điểm và phần trăm tính cộng dồn tới mốc đó. Dựng lại từ dữ liệu hiện có, không lưu lịch sử riêng,
     * nên sản phẩm đã gỡ sẽ không còn trên lộ trình và điểm dùng impactIndex hiện tại.
     */
    @Transactional(readOnly = true)
    public SkillTimelineResponse getTimeline(Long childId) {
        ChildProfile child = accessGuard.getOwnedChildProfile(childId);
        List<Skill> skills = skillRepository.findAll(Sort.by("id"));

        Map<Long, Long> cumulative = new HashMap<>();
        List<SkillTimelinePointResponse> points = new ArrayList<>();
        int count = 0;

        for (ChildProduct childProduct : childProductRepository.findTimelineByChildProfileId(child.getId())) {
            for (ProductSkillImpact impact : childProduct.getProduct().getProductSkillImpacts()) {
                cumulative.merge(impact.getSkill().getId(), (long) impact.getImpactIndex(), Long::sum);
            }
            count++;

            long totalImpact = cumulative.values().stream().mapToLong(Long::longValue).sum();
            List<SkillScoreResponse> scores = skills.stream()
                    .map(skill -> {
                        long score = cumulative.getOrDefault(skill.getId(), 0L);
                        return new SkillScoreResponse(skill.getId(), skill.getCode(), skill.getName(),
                                score, toPercentage(score, totalImpact));
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
