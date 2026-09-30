package com.brainblocks.backend.service.category;

import com.brainblocks.backend.dto.request.category.CategoryRequest;
import com.brainblocks.backend.dto.response.category.CategoryResponse;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    // public: hiển thị bộ lọc danh mục ở trang sản phẩm
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return toResponse(findCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        // kiểm tra trước để trả lỗi rõ ràng, không để văng lỗi unique constraint từ DB
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category name already exists");
        }

        Category category = Category.builder()
                .name(name)
                .description(blankToNull(request.description()))
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();

        // chỉ chặn trùng khi đổi sang tên của MỘT category khác; giữ nguyên tên cũ (chỉ khác hoa/thường) vẫn hợp lệ
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Category name already exists");
                });

        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        return toResponse(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);

        // Category.products không cascade: phải chặn ở service, không để lỗi khóa ngoại từ DB
        if (!category.getProducts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete category that still has products, move or delete them first");
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
