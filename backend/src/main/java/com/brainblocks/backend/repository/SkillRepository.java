package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    boolean existsByCode(String code);

    Optional<Skill> findByCode(String code);

    List<Skill> findAllByOrderByIdAsc();

    // skill còn được dùng ở chỉ số sản phẩm hoặc hồ sơ trẻ thì không cho xóa
    @Query("select (count(psi) > 0) from ProductSkillImpact psi where psi.skill.id = :skillId")
    boolean isUsedByProducts(@Param("skillId") Long skillId);

    @Query("select (count(c) > 0) from ChildProfile c join c.interestedSkills s where s.id = :skillId")
    boolean isUsedByChildProfiles(@Param("skillId") Long skillId);

    // xóa điểm của nhóm kỹ năng trong mọi hồ sơ kỹ năng (dữ liệu suy ra) trước khi xóa chính nhóm đó
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SkillScore s where s.skill.id = :skillId")
    int deleteScoresBySkillId(@Param("skillId") Long skillId);

    // thống kê nhóm kỹ năng phụ huynh quan tâm (đề 2.14)
    @Query("""
            select s.id as skillId, s.name as skillName, count(c) as total
            from ChildProfile c join c.interestedSkills s
            group by s.id, s.name
            """)
    List<SkillInterestCount> countInterestedChildren();

    interface SkillInterestCount {
        Long getSkillId();
        String getSkillName();
        Long getTotal();
    }
}
