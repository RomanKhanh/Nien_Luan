package com.brainblocks.backend.controller;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.ProductRecommendationResponse;
import com.brainblocks.backend.dto.response.SkillProfileResponse;
import com.brainblocks.backend.dto.response.SkillTimelineResponse;
import com.brainblocks.backend.service.skill.SkillProfileService;
import com.brainblocks.backend.service.skill.SkillRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/children/{id}/skill-profile")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class SkillProfileController {
    private final SkillProfileService skillProfileService;
    private final SkillRecommendationService skillRecommendationService;

    @GetMapping
    public ResponseEntity<ApiResponse<SkillProfileResponse>> getSkillProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(skillProfileService.getSkillProfile(id)));
    }

    @PostMapping("/recalculate")
    public ResponseEntity<ApiResponse<SkillProfileResponse>> recalculate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Skill profile recalculated", skillProfileService.recalculate(id)));
    }

    // dữ liệu cho biểu đồ lộ trình: mỗi mốc là một lần bé có thêm sản phẩm
    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<SkillTimelineResponse>> getTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(skillProfileService.getTimeline(id)));
    }

    // limit ngoài khoảng 1..20 được đưa về biên gần nhất
    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<ProductRecommendationResponse>>> recommendNext(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success(skillRecommendationService.recommendNext(id, limit)));
    }
}
