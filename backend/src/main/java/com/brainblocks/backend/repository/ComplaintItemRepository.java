package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ComplaintItem;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
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

    // tổng số món sản phẩm productId của bé childProfileId đã được duyệt trả (complaint RETURN đã RESOLVED)
    @Query("""
            select coalesce(sum(ci.quantity), 0) from ComplaintItem ci
            where ci.orderItem.childProfile.id = :childProfileId
              and ci.orderItem.product.id = :productId
              and ci.complaint.type = :type
              and ci.complaint.status = :status
            """)
    long sumQuantityByChildAndProductAndComplaint(@Param("childProfileId") Long childProfileId,
                                                  @Param("productId") Long productId,
                                                  @Param("type") ComplaintType type,
                                                  @Param("status") ComplaintStatus status);
}