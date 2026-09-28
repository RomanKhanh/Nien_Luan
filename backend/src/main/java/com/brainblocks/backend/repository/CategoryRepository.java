package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Category> findAllByOrderByNameAsc();

    // số sản phẩm đang bán của mọi danh mục trong 1 câu; danh mục chưa có sản phẩm nào không có dòng
    @Query("""
            select p.category.id as categoryId, count(p) as total
            from Product p
            where p.active = true
            group by p.category.id
            """)
    List<CategoryProductCount> countActiveProducts();

    // còn sản phẩm (kể cả đã ẩn) thì không cho xóa danh mục, vì category_id của sản phẩm là NOT NULL
    @Query("select (count(p) > 0) from Product p where p.category.id = :categoryId")
    boolean hasProducts(@Param("categoryId") Long categoryId);

    interface CategoryProductCount {
        Long getCategoryId();
        Long getTotal();
    }
}
