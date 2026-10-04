package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Voucher;
import com.brainblocks.backend.enums.VoucherReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    @EntityGraph(attributePaths = "order")
    List<Voucher> findByCustomerIdOrderByIssuedAtDesc(Long customerId);

    List<Voucher> findByOrderId(Long orderId);

    // các mốc kỹ năng đã tặng cho một bé, để mỗi mốc chỉ tặng 1 lần
    @Query("select distinct v.reason from Voucher v where v.childProfileId = :childProfileId")
    Set<VoucherReason> findReasonsByChildProfileId(@Param("childProfileId") Long childProfileId);

    // ===== trang admin =====

    @EntityGraph(attributePaths = {"customer", "order"})
    @Query("select v from Voucher v")
    Page<Voucher> findAllForAdmin(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "order"})
    @Query("select v from Voucher v where v.order is not null")
    Page<Voucher> findUsed(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "order"})
    @Query("select v from Voucher v where v.order is null and v.expiresAt >= :now")
    Page<Voucher> findAvailable(@Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "order"})
    @Query("select v from Voucher v where v.order is null and v.expiresAt < :now")
    Page<Voucher> findExpired(@Param("now") LocalDateTime now, Pageable pageable);

    long countByOrderIsNotNull();

    @Query("select count(v) from Voucher v where v.order is null and v.expiresAt >= :now")
    long countAvailable(@Param("now") LocalDateTime now);

    @Query("select count(v) from Voucher v where v.order is null and v.expiresAt < :now")
    long countExpired(@Param("now") LocalDateTime now);
}
