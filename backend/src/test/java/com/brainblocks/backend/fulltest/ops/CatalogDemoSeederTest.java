package com.brainblocks.backend.fulltest.ops;

import com.brainblocks.backend.config.CatalogSeeder;
import com.brainblocks.backend.entity.Category;
import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.entity.Skill;
import com.brainblocks.backend.repository.CategoryRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogDemoSeederTest {
    @Test
    void enabledDemoSeederCreatesCatalogOnceAndSecondRunDoesNotDuplicateAnything() {
        SkillRepository skills = mock(SkillRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        Map<String, Skill> savedSkills = new HashMap<>();
        List<Category> savedCategories = new ArrayList<>();
        List<Product> savedProducts = new ArrayList<>();
        when(skills.existsByCode(anyString())).thenAnswer(call -> savedSkills.containsKey(call.getArgument(0)));
        when(skills.save(any(Skill.class))).thenAnswer(call -> {
            Skill skill = call.getArgument(0);
            savedSkills.put(skill.getCode(), skill);
            return skill;
        });
        when(skills.findByCode(anyString())).thenAnswer(call -> Optional.ofNullable(savedSkills.get(call.getArgument(0))));
        when(categories.findAll()).thenAnswer(call -> List.copyOf(savedCategories));
        when(categories.save(any(Category.class))).thenAnswer(call -> {
            Category category = call.getArgument(0);
            savedCategories.add(category);
            return category;
        });
        when(products.count()).thenAnswer(call -> (long) savedProducts.size());
        when(products.save(any(Product.class))).thenAnswer(call -> {
            Product product = call.getArgument(0);
            savedProducts.add(product);
            return product;
        });
        CatalogSeeder seeder = new CatalogSeeder(skills, categories, products);
        ReflectionTestUtils.setField(seeder, "seedDemoData", true);
        seeder.run();
        assertThat(savedSkills).hasSize(4);
        assertThat(savedCategories).hasSize(6);
        assertThat(savedProducts).hasSize(16);
        List<Product> firstProducts = List.copyOf(savedProducts);
        List<Category> firstCategories = List.copyOf(savedCategories);
        seeder.run();
        assertThat(savedSkills).hasSize(4);
        assertThat(savedProducts).containsExactlyElementsOf(firstProducts);
        assertThat(savedCategories).containsExactlyElementsOf(firstCategories);
        verify(skills, times(4)).save(any(Skill.class));
        verify(categories, times(6)).save(any(Category.class));
        verify(products, times(16)).save(any(Product.class));
    }
}
