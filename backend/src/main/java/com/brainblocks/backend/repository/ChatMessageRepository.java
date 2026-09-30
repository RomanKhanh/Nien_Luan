package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.ChatMessage;
import com.brainblocks.backend.enums.SenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    long countBySender(SenderType sender);

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
