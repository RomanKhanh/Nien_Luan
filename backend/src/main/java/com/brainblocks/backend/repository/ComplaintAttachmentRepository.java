package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ComplaintAttachment;
import com.brainblocks.backend.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, Long> {
    // ràng buộc complaintId để không lấy nhầm file của khiếu nại khác; kèm khách để kiểm tra quyền
    @EntityGraph(attributePaths = {"complaint", "complaint.customer"})
    Optional<ComplaintAttachment> findByIdAndComplaintId(Long id, Long complaintId);

    // file còn trên đĩa của các yêu cầu đã đóng trước cutoff và không được admin giữ lại (EvidenceRetention)
    @Query("""
            select a from ComplaintAttachment a
            join a.complaint c
            where a.purgedAt is null
              and c.evidenceHold = false
              and c.status in :closedStatuses
              and c.handledAt < :cutoff
            """)
    List<ComplaintAttachment> findPurgeable(@Param("closedStatuses") Collection<ComplaintStatus> closedStatuses,
                                            @Param("cutoff") LocalDateTime cutoff);
}
