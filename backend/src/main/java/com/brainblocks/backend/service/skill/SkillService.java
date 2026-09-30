package com.brainblocks.backend.service.skill;

import com.brainblocks.backend.dto.request.skill.SkillRequest;
import com.brainblocks.backend.dto.response.SkillResponse;
import com.brainblocks.backend.dto.response.skill.SkillDetailResponse;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SkillService {
    private final SkillRepository skillRepository;

    // dùng ở cả 2 phía: khách chọn interestedSkillIds khi tạo hồ sơ trẻ, admin xem danh sách
    @Transactional(readOnly = true)
    public List<SkillResponse> getAll() {
        return skillRepository.findAll(Sort.by("id")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillDetailResponse getDetail(Long id) {
        return toDetailResponse(findSkill(id));
    }

    @Transactional
    public SkillDetailResponse create(SkillRequest request) {
        String code = normalizeCode(request.code());
        // kiểm tra trước để trả lỗi rõ ràng, không để văng lỗi unique constraint từ DB
        if (skillRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Skill code already exists");
        }

        Skill skill = Skill.builder()
                .code(code)
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .build();
        return toDetailResponse(skillRepository.save(skill));
    }

    // code là định danh dùng ở nơi khác (chatbot, seed data) nên không cho đổi sau khi tạo,
    // chỉ nhận name/description mới; nếu client gửi code khác thì coi là nhầm lẫn và báo lỗi luôn
    @Transactional
    public SkillDetailResponse update(Long id, SkillRequest request) {
        Skill skill = findSkill(id);
        String requestedCode = normalizeCode(request.code());
        if (!skill.getCode().equalsIgnoreCase(requestedCode)) {
            throw new IllegalArgumentException("Skill code cannot be changed after creation");
        }

        skill.setName(request.name().trim());
        skill.setDescription(blankToNull(request.description()));
        return toDetailResponse(skill);
    }

    // chỉ xóa được khi chưa có sản phẩm nào gán impact và chưa bé nào có điểm cho nhóm này;
    // vì applyScores tạo SkillScore = 0 cho MỌI skill ngay khi tính lại hồ sơ đầu tiên, trên thực tế
    // thao tác này gần như chỉ dùng được cho skill vừa tạo và chưa từng được dùng tới
    @Transactional
    public void delete(Long id) {
        Skill skill = findSkill(id);
        if (!skill.getProductSkillImpacts().isEmpty() || !skill.getSkillScores().isEmpty()) {
            throw new IllegalArgumentException("Cannot delete skill that is already in use");
        }
        skillRepository.delete(skill);
    }

    private Skill findSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private SkillResponse toResponse(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getCode(), skill.getName());
    }

    private SkillDetailResponse toDetailResponse(Skill skill) {
        return new SkillDetailResponse(skill.getId(), skill.getCode(), skill.getName(), skill.getDescription());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
