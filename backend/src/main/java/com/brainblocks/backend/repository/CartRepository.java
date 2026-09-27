package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query("""
            select distinct c from Cart c
            left join fetch c.items i
            left join fetch i.product
            where c.customer.id = :customerId
            """)
    Optional<Cart> findWithItemsByCustomerId(@Param("customerId") Long customerId);
}
