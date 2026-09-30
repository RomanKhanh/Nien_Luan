package com.brainblocks.backend.service.product;

import com.brainblocks.backend.dto.request.product.CategoryRequest;
import com.brainblocks.backend.dto.response.product.CategoryResponse;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.CategoryRepository.CategoryProductCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// danh mục sản phẩm: xem công khai, admin thêm / sửa / xóa (đề 2.9)
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        Map<Long, Long> countByCategory = categoryRepository.countActiveProducts().stream()
                .collect(Collectors.toMap(CategoryProductCount::getCategoryId, CategoryProductCount::getTotal));
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> toResponse(c, countByCategory.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        // kiểm tra trước để trả lỗi rõ ràng; uk_categories_name vẫn chặn ở DB nếu 2 request chạy song song
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category name already exists");
        }
        Category category = categoryRepository.save(Category.builder()
                .name(name)
                .description(blankToNull(request.description()))
                .build());
        return toResponse(category, 0);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Category name already exists");
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        long count = categoryRepository.countActiveProducts().stream()
                .filter(row -> row.getCategoryId().equals(id))
                .mapToLong(CategoryProductCount::getTotal)
                .findFirst().orElse(0);
        return toResponse(category, count);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        // sản phẩm bắt buộc có danh mục: phải chuyển sản phẩm sang danh mục khác trước khi xóa
        if (categoryRepository.hasProducts(id)) {
            throw new IllegalArgumentException("Category still has products, move them to another category first");
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private CategoryResponse toResponse(Category category, long productCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), productCount);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
