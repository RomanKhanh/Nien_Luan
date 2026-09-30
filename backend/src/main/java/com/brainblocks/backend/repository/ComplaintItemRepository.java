package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ComplaintItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintItemRepository extends JpaRepository<ComplaintItem, Long> {
    // dùng cho list admin (đã phân trang): lấy items của cả trang trong 1 câu thay vì lazy-load từng complaint
    @Query("""
            select ci from ComplaintItem ci
            join fetch ci.orderItem oi
            join fetch oi.product
            where ci.complaint.id in :complaintIds
            """)
    List<ComplaintItem> findAllWithOrderItemAndProductByComplaintIdIn(@Param("complaintIds") List<Long> complaintIds);
}