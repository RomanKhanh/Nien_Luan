package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// JpaRepository cung cấp sẵn save, findById, delete và các thao tác CRUD cho ChatSession, khóa Long.
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long>, JpaSpecificationExecutor<ChatSession> {

    // Khóa đúng bảng phiên chat; không khóa outer join tới hồ sơ bé (PostgreSQL không cho phép).
    @Query(value = "select id from chat_sessions where id = :id for update", nativeQuery = true)
    Optional<Long> lockId(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "childProfile"})
    Page<ChatSession> findAll(Specification<ChatSession> spec, Pageable pageable);

    // Danh sách hội thoại của một khách; mới nhất trước, id để ổn định thứ tự khi trùng thời gian.
    // EntityGraph lấy luôn hồ sơ bé (nếu có), không tải toàn bộ tin nhắn cho trang danh sách.
    @EntityGraph(attributePaths = "childProfile")
    List<ChatSession> findAllByCustomerIdOrderByStartedAtDescIdDesc(Long customerId);

    // Tìm theo cả chủ sở hữu: hội thoại của khách khác trả Optional rỗng.
    @EntityGraph(attributePaths = {"customer", "childProfile"})
    Optional<ChatSession> findByIdAndCustomerId(Long id, Long customerId);
}
