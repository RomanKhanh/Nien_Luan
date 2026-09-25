package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
    // skillProfile là phía mappedBy của @OneToOne nên Hibernate không lazy được, còn interestedSkills
    // được map ra response; fetch cả hai trong 1 câu để tránh mỗi hồ sơ trẻ phát sinh thêm query
    @Query("""
            select distinct c from ChildProfile c
            left join fetch c.skillProfile
            left join fetch c.interestedSkills
            where c.customer.id = :customerId
            order by c.createdAt asc
            """)
    List<ChildProfile> findAllByCustomerId(@Param("customerId") Long customerId);

    // Gỡ liên kết "dành cho" trước khi xóa hồ sơ trẻ: dòng đơn hàng là dữ liệu lịch sử nên giữ lại,
    // chỉ bỏ tham chiếu tới bé để không vướng khóa ngoại
    @Modifying(flushAutomatically = true)
    @Query("update OrderItem oi set oi.childProfile = null where oi.childProfile.id = :childProfileId")
    int detachOrderItems(@Param("childProfileId") Long childProfileId);
}
