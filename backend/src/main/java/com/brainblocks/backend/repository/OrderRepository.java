package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product
            where o.id = :id
            """)
    Optional<Order> findWithItemsById(@Param("id") Long id);

    @Query("""
            select distinct o from Order o
            left join fetch o.items i
            left join fetch i.product
            where o.customer.id = :customerId
            order by o.createdAt desc
            """)
    List<Order> findAllWithItemsByCustomerId(
            @Param("customerId") Long customerId
    );

    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

}
