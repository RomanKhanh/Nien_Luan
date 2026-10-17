package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChatbotConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, Long> {

    // Mọi lần kích hoạt/sửa dùng cùng một dòng làm khóa, kể cả khi chưa có config active.
    @Query(value = "select id from chatbot_configs order by id limit 1 for update", nativeQuery = true)
    Optional<Long> lockActivationAnchor();

    // Cấu hình đang dùng; không dùng findFirst để tránh che giấu trường hợp nhiều cấu hình active.
    // Service phải bảo đảm chỉ một cấu hình active trong lúc kích hoạt cấu hình mới.
    @EntityGraph(attributePaths = "admin")
    Optional<ChatbotConfig> findByActiveTrue();

    // Lịch sử các phiên bản cấu hình; lấy luôn admin tạo cấu hình để hiển thị.
    @EntityGraph(attributePaths = "admin")
    List<ChatbotConfig> findAllByOrderByUpdatedAtDescIdDesc();
}
