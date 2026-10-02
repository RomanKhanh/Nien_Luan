package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Complaint;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.ComplaintType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    @Query("""
            select distinct c from Complaint c
            join fetch c.order o
            join fetch c.customer
            left join fetch c.handledBy
            left join fetch c.items ci
            left join fetch ci.orderItem oi
            left join fetch oi.product
            where c.customer.id = :customerId
            order by c.createdAt desc
            """)
    List<Complaint> findAllByCustomerId(@Param("customerId") Long customerId);

    @EntityGraph(attributePaths = {"order", "customer", "handledBy"})
    Page<Complaint> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"order", "customer", "handledBy"})
    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);

    long countByStatus(ComplaintStatus status);

    // yêu cầu đã được tiếp nhận trước mốc cutoff mà vẫn đang chờ (ReturnPolicy: quá hạn gửi hàng về)
    @Query("""
            select c from Complaint c
            join fetch c.order
            join fetch c.customer
            where c.type in :types and c.status = :status and c.acceptedAt < :cutoff
            """)
    List<Complaint> findAcceptedBefore(@Param("types") Collection<ComplaintType> types,
                                       @Param("status") ComplaintStatus status,
                                       @Param("cutoff") LocalDateTime cutoff);
}
