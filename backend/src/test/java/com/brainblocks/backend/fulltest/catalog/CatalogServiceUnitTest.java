package com.brainblocks.backend.fulltest.catalog;

import com.brainblocks.backend.dto.request.product.CategoryRequest;
import com.brainblocks.backend.dto.request.product.SkillRequest;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.OrderItemRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.ProductSkillImpactRepository;
import com.brainblocks.backend.repository.ReviewRepository;
import com.brainblocks.backend.repository.SkillRepository;
import com.brainblocks.backend.service.product.CategoryService;
import com.brainblocks.backend.service.product.ProductSearchCriteria;
import com.brainblocks.backend.service.product.ProductService;
import com.brainblocks.backend.service.product.ProductSort;
import com.brainblocks.backend.service.skill.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceUnitTest {
    @Mock ProductRepository products;
    @Mock ProductSkillImpactRepository impacts;
    @Mock ReviewRepository reviews;
    @Mock OrderItemRepository orderItems;
    @Mock CategoryRepository categories;
    @Mock SkillRepository skills;
    @Mock ApplicationEventPublisher events;
    private ProductService productService;
    private CategoryService categoryService;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(products, impacts, reviews, orderItems);
        categoryService = new CategoryService(categories, events);
        skillService = new SkillService(skills, events);
    }

    private ProductSearchCriteria criteria(BigDecimal min, BigDecimal max) {
        return new ProductSearchCriteria(null, null, null, null, List.of(), min, max, false, false, null);
    }

    @ParameterizedTest
    @CsvSource({"price_asc,PRICE_ASC", "price_desc,PRICE_DESC", "newest,NEWEST", "relevance,RELEVANCE", "' PRICE_ASC ',PRICE_ASC"})
    void parsesPublicSortValues(String input, ProductSort expected) {
        assertThat(ProductSort.from(input)).isEqualTo(expected);
    }

    @Test
    void defaultsToRelevanceAndRejectsUnknownSort() {
        assertThat(ProductSort.from(null)).isEqualTo(ProductSort.RELEVANCE);
        assertThat(ProductSort.from(" ")).isEqualTo(ProductSort.RELEVANCE);
        assertThatThrownBy(() -> ProductSort.from("random")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reversedPriceRangeFailsBeforeDatabaseAccess() {
        assertThatThrownBy(() -> productService.search(criteria(new BigDecimal("200"), new BigDecimal("100")),
                ProductSort.NEWEST, 0, 12)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
        verifyNoInteractions(products, impacts, reviews, orderItems);
    }

    @ParameterizedTest
    @CsvSource({"-1,12", "0,-1", "0,0"})
    void invalidPaginationFailsBeforeDatabaseAccess(int page, int size) {
        assertThatThrownBy(() -> productService.search(criteria(null, null), ProductSort.NEWEST, page, size))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(products);
    }

    @ParameterizedTest
    @CsvSource({"PRICE_ASC,price,ASC", "PRICE_DESC,price,DESC", "NEWEST,createdAt,DESC"})
    void searchCapsPageSizeAndUsesStableSecondarySort(ProductSort sort, String field, Sort.Direction direction) {
        when(products.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        productService.search(criteria(null, null), sort, 2, 10000);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
        assertThat(page.getValue().getSort().getOrderFor(field)).isNotNull();
        assertThat(page.getValue().getSort().getOrderFor(field).getDirection()).isEqualTo(direction);
        assertThat(page.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void relevanceDoesNotOverrideSpecificationOrdering() {
        when(products.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        productService.search(criteria(null, null), ProductSort.RELEVANCE, 0, 12);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(products).findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), page.capture());
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hiddenOrMissingProductIsNotFoundBeforeMapping(boolean hidden) {
        when(products.findWithCategoryById(1L)).thenReturn(hidden
                ? Optional.of(Product.builder().id(1L).active(false).build()) : Optional.empty());
        assertThatThrownBy(() -> productService.getActiveProduct(1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(impacts, reviews, orderItems);
    }

    @Test
    void duplicateCategoryIsRejectedBeforeSave() {
        when(categories.existsByNameIgnoreCase("Blocks")).thenReturn(true);
        assertThatThrownBy(() -> categoryService.createCategory(new CategoryRequest(" Blocks ", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        verify(categories, never()).save(any(Category.class));
    }

    @Test
    void categoryWithProductsCannotBeDeleted() {
        when(categories.findById(1L)).thenReturn(Optional.of(Category.builder().id(1L).name("Blocks").build()));
        when(categories.hasProducts(1L)).thenReturn(true);
        assertThatThrownBy(() -> categoryService.deleteCategory(1L)).isInstanceOf(IllegalArgumentException.class);
        verify(categories, never()).delete(any(Category.class));
    }

    @Test
    void duplicateSkillCodeIsRejectedBeforeSave() {
        when(skills.existsByCode("LOGIC")).thenReturn(true);
        assertThatThrownBy(() -> skillService.createSkill(new SkillRequest("LOGIC", "Logic", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        verify(skills, never()).save(any(Skill.class));
    }

    @Test
    void changingSkillCodeLeavesEntityUnchangedAndPublishesNoEvent() {
        Skill skill = Skill.builder().id(1L).code("LOGIC").name("Original").build();
        when(skills.findById(1L)).thenReturn(Optional.of(skill));
        assertThatThrownBy(() -> skillService.updateSkill(1L, new SkillRequest("STEM", "New", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be changed");
        assertThat(skill.getCode()).isEqualTo("LOGIC");
        assertThat(skill.getName()).isEqualTo("Original");
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void skillReferencedByProductOrChildCannotBeDeleted(boolean usedByProduct) {
        when(skills.findById(1L)).thenReturn(Optional.of(Skill.builder().id(1L).code("LOGIC").build()));
        when(skills.isUsedByProducts(1L)).thenReturn(usedByProduct);
        if (!usedByProduct) when(skills.isUsedByChildProfiles(1L)).thenReturn(true);
        assertThatThrownBy(() -> skillService.deleteSkill(1L)).isInstanceOf(IllegalArgumentException.class);
        verify(skills, never()).deleteScoresBySkillId(any());
        verify(skills, never()).deleteById(any());
    }

    @Test
    void unusedSkillDeletesDerivedScoresBeforeDeletingSkill() {
        when(skills.findById(1L)).thenReturn(Optional.of(Skill.builder().id(1L).code("CUSTOM").build()));
        skillService.deleteSkill(1L);
        var order = inOrder(skills);
        order.verify(skills).deleteScoresBySkillId(1L);
        order.verify(skills).deleteById(1L);
    }
}
