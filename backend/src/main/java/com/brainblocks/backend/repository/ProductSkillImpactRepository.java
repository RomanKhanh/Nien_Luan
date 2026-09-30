package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ProductSkillImpact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductSkillImpactRepository extends JpaRepository<ProductSkillImpact, Long> {
    // chỉ số tác động của cả trang sản phẩm trong 1 câu, tránh N+1 khi dựng danh sách
    @Query("""
            select psi from ProductSkillImpact psi
            join fetch psi.skill
            where psi.product.id in :productIds
            order by psi.skill.id
            """)
    List<ProductSkillImpact> findAllWithSkillByProductIdIn(@Param("productIds") List<Long> productIds);
}
