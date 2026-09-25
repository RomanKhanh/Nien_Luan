package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChildProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChildProductRepository extends JpaRepository<ChildProduct, Long> {

    @Query("""
            select cp from ChildProduct cp
            join fetch cp.product
            where cp.childProfile.id = :childProfileId
            order by cp.addedAt desc
            """)
    List<ChildProduct> findAllWithProductByChildProfileId(@Param("childProfileId") Long childProfileId);

    // dữ liệu lộ trình: sản phẩm của bé theo thứ tự thêm vào, kèm chỉ số tác động trong 1 câu
    @Query("""
            select distinct cp from ChildProduct cp
            join fetch cp.product p
            left join fetch p.productSkillImpacts psi
            left join fetch psi.skill
            where cp.childProfile.id = :childProfileId
            order by cp.addedAt asc, cp.id asc
            """)
    List<ChildProduct> findTimelineByChildProfileId(@Param("childProfileId") Long childProfileId);

    // ràng buộc thêm childProfileId để không gỡ nhầm sản phẩm của hồ sơ trẻ khác
    Optional<ChildProduct> findByIdAndChildProfileId(Long id, Long childProfileId);

    boolean existsByChildProfileIdAndProductId(Long childProfileId, Long productId);

    long countByChildProfileId(Long childProfileId);

    // gộp ChildProduct -> Product -> ProductSkillImpact trong 1 câu, cộng impactIndex theo từng skill
    @Query("""
            select psi.skill.id as skillId, sum(psi.impactIndex) as totalImpact
            from ChildProduct cp
            join cp.product p
            join p.productSkillImpacts psi
            where cp.childProfile.id = :childProfileId
            group by psi.skill.id
            """)
    List<SkillImpactSum> sumImpactBySkill(@Param("childProfileId") Long childProfileId);

    // ===== dùng khi tính lại theo lô cho nhiều hồ sơ trẻ (vd admin sửa impactIndex của một sản phẩm) =====

    @Query("select distinct cp.childProfile.id from ChildProduct cp where cp.product.id = :productId")
    List<Long> findChildProfileIdsByProductId(@Param("productId") Long productId);

    @Query("""
            select cp.childProfile.id as childProfileId, psi.skill.id as skillId, sum(psi.impactIndex) as totalImpact
            from ChildProduct cp
            join cp.product p
            join p.productSkillImpacts psi
            where cp.childProfile.id in :childProfileIds
            group by cp.childProfile.id, psi.skill.id
            """)
    List<ChildSkillImpactSum> sumImpactBySkillForChildren(@Param("childProfileIds") List<Long> childProfileIds);

    @Query("""
            select cp.childProfile.id as childProfileId, count(cp) as total
            from ChildProduct cp
            where cp.childProfile.id in :childProfileIds
            group by cp.childProfile.id
            """)
    List<ChildProductCount> countByChildProfileIds(@Param("childProfileIds") List<Long> childProfileIds);

    interface SkillImpactSum {
        Long getSkillId();

        Long getTotalImpact();
    }

    interface ChildSkillImpactSum {
        Long getChildProfileId();

        Long getSkillId();

        Long getTotalImpact();
    }

    interface ChildProductCount {
        Long getChildProfileId();

        Long getTotal();
    }
}
