package com.brainblocks.backend.service.skill;

import com.brainblocks.backend.dto.request.product.SkillRequest;
import com.brainblocks.backend.dto.response.SkillDetailResponse;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// nhóm kỹ năng: xem công khai, admin quản lý (đề 2.9)
@Service
@RequiredArgsConstructor
public class SkillService {
    private final SkillRepository skillRepository;

    @Transactional(readOnly = true)
    public List<SkillDetailResponse> getSkills() {
        return skillRepository.findAllByOrderByIdAsc().stream().map(this::toResponse).toList();
    }

    /**
     * Thêm nhóm kỹ năng mới. Hồ sơ kỹ năng của các bé chưa có điểm cho nhóm này;
     * SkillProfileService tự bổ sung SkillScore = 0 ở lần tính lại kế tiếp.
     */
    @Transactional
    public SkillDetailResponse createSkill(SkillRequest request) {
        if (skillRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Skill code already exists");
        }
        return toResponse(skillRepository.save(Skill.builder()
                .code(request.code())
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .build()));
    }

    // mã kỹ năng không đổi được: frontend (màu nhóm) và chatbot tham chiếu theo mã
    @Transactional
    public SkillDetailResponse updateSkill(Long id, SkillRequest request) {
        Skill skill = findSkill(id);
        if (!skill.getCode().equals(request.code())) {
            throw new IllegalArgumentException("Skill code cannot be changed");
        }
        skill.setName(request.name().trim());
        skill.setDescription(blankToNull(request.description()));
        return toResponse(skill);
    }

    @Transactional
    public void deleteSkill(Long id) {
        findSkill(id);
        if (skillRepository.isUsedByProducts(id) || skillRepository.isUsedByChildProfiles(id)) {
            throw new IllegalArgumentException("Skill is still used by products or child profiles");
        }
        // SkillScore của nhóm này trong các hồ sơ kỹ năng là dữ liệu suy ra (luôn = 0 vì không sản phẩm nào dùng), xóa theo
        skillRepository.deleteScoresBySkillId(id);
        skillRepository.deleteById(id);
    }

    private Skill findSkill(Long id) {
        return skillRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    private SkillDetailResponse toResponse(Skill skill) {
        return new SkillDetailResponse(skill.getId(), skill.getCode(), skill.getName(), skill.getDescription());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
