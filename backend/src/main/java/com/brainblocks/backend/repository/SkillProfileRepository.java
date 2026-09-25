package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.SkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {
    // lấy hồ sơ kỹ năng kèm toàn bộ SkillScore và Skill trong 1 câu
    @Query("""
            select distinct sp from SkillProfile sp
            left join fetch sp.skillScores ss
            left join fetch ss.skill
            where sp.childProfile.id = :childProfileId
            """)
    Optional<SkillProfile> findWithScoresByChildProfileId(@Param("childProfileId") Long childProfileId);

    @Query("""
            select distinct sp from SkillProfile sp
            left join fetch sp.skillScores ss
            left join fetch ss.skill
            where sp.childProfile.id in :childProfileIds
            """)
    List<SkillProfile> findAllWithScoresByChildProfileIdIn(@Param("childProfileIds") List<Long> childProfileIds);
}
