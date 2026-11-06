package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Voucher;
import com.brainblocks.backend.enums.VoucherReason;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    @EntityGraph(attributePaths = "order")
    List<Voucher> findByCustomerIdOrderByIssuedAtDesc(Long customerId);

    List<Voucher> findByOrderId(Long orderId);

    // các mốc kỹ năng đã tặng cho một bé, để mỗi mốc chỉ tặng 1 lần
    @Query("select distinct v.reason from Voucher v where v.childProfileId = :childProfileId")
    Set<VoucherReason> findReasonsByChildProfileId(@Param("childProfileId") Long childProfileId);

}
