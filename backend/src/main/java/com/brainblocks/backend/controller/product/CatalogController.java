package com.brainblocks.backend.controller.product;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.SkillDetailResponse;
import com.brainblocks.backend.dto.response.product.CategoryResponse;
import com.brainblocks.backend.service.product.CategoryService;
import com.brainblocks.backend.service.skill.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// danh mục và nhóm kỹ năng cho bộ lọc sản phẩm, mở công khai (SecurityConfig)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CategoryService categoryService;
    private final SkillService skillService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategories()));
    }

    @GetMapping("/skills")
    public ResponseEntity<ApiResponse<List<SkillDetailResponse>>> getSkills() {
        return ResponseEntity.ok(ApiResponse.success(skillService.getSkills()));
    }
}
