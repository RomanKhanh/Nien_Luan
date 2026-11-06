package com.brainblocks.backend.fulltest.ops;

import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.product.SearchSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Accent, hidden-product and special-character cases already live in CatalogApiTest. */
class SearchSuggestionServiceTest {
    ProductRepository products;
    CategoryRepository categories;
    SkillRepository skills;
    SearchSuggestionService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        categories = mock(CategoryRepository.class);
        skills = mock(SkillRepository.class);
        service = new SearchSuggestionService(products, categories, skills);
    }

    @Test
    void singleCharacterOnlyMatchesStartOfNameOrWord() {
        when(products.findSuggestionCandidates(anyString(), any(Pageable.class))).thenReturn(List.of(
                product(1, "puzzle"), product(2, "zebra"), product(3, "blue zebra"), product(4, "buzz")));
        assertThat(service.suggest("z")).extracting(s -> s.id()).containsExactly(2L, 3L);
    }

    @Test
    void capsEachSuggestionTypeIndependently() {
        List<Product> candidateProducts = new ArrayList<>();
        List<Category> candidateCategories = new ArrayList<>();
        List<Skill> candidateSkills = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidateProducts.add(product(i + 1, "ops robot " + i));
            candidateCategories.add(Category.builder().id((long) i + 1).name("ops category " + i).build());
            candidateSkills.add(Skill.builder().id((long) i + 1).code("OPS_" + i).name("ops skill " + i).build());
        }
        when(products.findSuggestionCandidates(anyString(), any(Pageable.class))).thenReturn(candidateProducts);
        when(categories.findAll()).thenReturn(candidateCategories);
        when(skills.findAllByOrderByIdAsc()).thenReturn(candidateSkills);
        var result = service.suggest("ops");
        assertThat(result).hasSize(11);
        assertThat(result.stream().filter(s -> s.type().equals("PRODUCT")).count()).isEqualTo(6);
        assertThat(result.stream().filter(s -> s.type().equals("CATEGORY")).count()).isEqualTo(3);
        assertThat(result.stream().filter(s -> s.type().equals("SKILL")).count()).isEqualTo(2);
        assertThat(result.stream().map(s -> s.type()).toList()).containsExactly(
                "PRODUCT", "PRODUCT", "PRODUCT", "PRODUCT", "PRODUCT", "PRODUCT", "CATEGORY", "CATEGORY", "CATEGORY", "SKILL", "SKILL");
    }

    @Test
    void ranksNameMatchesAndRejectsDescriptionOnlyCandidates() {
        when(products.findSuggestionCandidates(anyString(), any(Pageable.class))).thenReturn(List.of(
                product(1, "car robot kit"), product(2, "robot"), product(3, "robot kit"),
                product(4, "microbot"), product(5, "chemistry kit")));
        assertThat(service.suggest("robot")).extracting(s -> s.id()).containsExactly(2L, 3L, 1L, 4L);
        assertThat(service.suggest("kit robot")).extracting(s -> s.id()).containsExactly(3L, 1L);
    }

    @Test
    void longQueryIsTruncatedBeforeDatabaseLookup() {
        assertThat(service.suggest("x".repeat(1000))).isEmpty();
        verify(products).findSuggestionCandidates(eq("%" + "x".repeat(100) + "%"),
                argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 200));
        verify(products, never()).findThumbnails(any());
    }

    private Product product(long id, String name) {
        return Product.builder().id(id).name(name).price(BigDecimal.TEN).build();
    }
}
