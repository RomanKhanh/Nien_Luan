package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChatMessage;
import com.brainblocks.backend.enums.SenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Lịch sử từ cũ đến mới; id giữ thứ tự ổn định khi USER và BOT có cùng createdAt.
    // Lấy luôn sản phẩm gợi ý để dựng câu trả lời mà không query riêng cho từng tin nhắn.
    @EntityGraph(attributePaths = "suggestedProducts")
    List<ChatMessage> findAllByChatSessionIdOrderByCreatedAtAscIdAsc(Long chatSessionId);

    long countBySender(SenderType sender);
    long countBySenderAndCreatedAtGreaterThanEqual(SenderType sender, LocalDateTime since);

    @Query("select m.sender as sender, m.content as content from ChatMessage m where m.chatSession.id = :id order by m.id desc")
    List<PromptMessage> recentMessages(@Param("id") Long id, Pageable pageable);

    interface PromptMessage { SenderType getSender(); String getContent(); }

    // Tổng hợp cả trang hội thoại một lần thay vì tải tin nhắn từng phiên.
    @Query("select m.chatSession.id as sessionId, count(m) as total, max(m.createdAt) as lastMessageAt, "
            + "max(m.id) as lastMessageId from ChatMessage m where m.chatSession.id in :ids group by m.chatSession.id")
    List<SessionMessages> summarizeSessions(@Param("ids") List<Long> ids);

    @Query("select avg(m.responseTimeMs) from ChatMessage m where m.sender = :sender")
    Double averageResponseTime(@Param("sender") SenderType sender);

    @Query("select lower(trim(m.content)) as question, count(m) as total from ChatMessage m "
            + "where m.sender = :sender group by lower(trim(m.content)) order by count(m) desc, lower(trim(m.content))")
    List<QuestionCount> topQuestions(@Param("sender") SenderType sender, Pageable pageable);

    @Query("select p.id as productId, p.name as productName, count(m) as total from ChatMessage m "
            + "join m.suggestedProducts p where m.sender = :sender group by p.id, p.name order by count(m) desc, p.id")
    List<SuggestedProductCount> topSuggestedProducts(@Param("sender") SenderType sender, Pageable pageable);

    @Query(value = "select cast(created_at as date) as questionDate, count(*) as total from chat_messages "
            + "where sender = 'USER' and created_at >= :since group by cast(created_at as date) "
            + "order by cast(created_at as date)", nativeQuery = true)
    List<DailyQuestionCount> dailyQuestions(@Param("since") LocalDateTime since);

    interface SessionMessages {
        Long getSessionId();
        Long getTotal();
        LocalDateTime getLastMessageAt();
        Long getLastMessageId();
    }
    interface QuestionCount { String getQuestion(); Long getTotal(); }
    interface SuggestedProductCount { Long getProductId(); String getProductName(); Long getTotal(); }
    interface DailyQuestionCount { LocalDate getQuestionDate(); Long getTotal(); }

    // chủ đề tư vấn phổ biến (đề 2.14); tin nhắn chưa gán chủ đề không tính
    @Query("""
            select m.topic as topic, count(m) as total from ChatMessage m
            where m.topic is not null
            group by m.topic
            order by count(m) desc
            """)
    List<TopicCount> countByTopic(Pageable pageable);

    interface TopicCount {
        String getTopic();
        Long getTotal();
    }
}
