package com.brainblocks.backend.controller.admin;

import com.brainblocks.backend.dto.request.skill.SkillRequest;
import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.skill.SkillDetailResponse;
import com.brainblocks.backend.service.skill.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/skills")
@RequiredArgsConstructor
public class AdminSkillController {
    private final SkillService skillService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(skillService.getDetail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillDetailResponse>> create(@Valid @RequestBody SkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Skill created", skillService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SkillDetailResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Skill updated", skillService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Skill deleted", null));
    }
}
