package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.enums.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "handledBy"})
    List<Complaint> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    // danh sách cho admin: lọc theo trạng thái, null = tất cả
    @EntityGraph(attributePaths = {"order", "customer", "orderItem", "orderItem.product", "handledBy"})
    @Query("select c from Complaint c where (:status is null or c.status = :status)")
    Page<Complaint> findForAdmin(@Param("status") ComplaintStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "customer", "orderItem", "orderItem.product", "handledBy"})
    Optional<Complaint> findWithDetailsById(Long id);

    long countByStatus(ComplaintStatus status);
}
