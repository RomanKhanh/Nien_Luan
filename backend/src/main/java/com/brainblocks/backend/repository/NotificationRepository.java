package com.brainblocks.backend.repository;

import com.brainblocks.backend.entity.Notification;
import com.brainblocks.backend.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    // số chưa đọc theo từng loại trong 1 câu (chuông khách hàng dùng tổng, menu admin dùng từng loại)
    @Query("""
            select n.type as type, count(n) as total
            from Notification n
            where n.recipient.id = :recipientId and n.read = false
            group by n.type
            """)
    List<TypeCount> countUnreadByType(@Param("recipientId") Long recipientId);

    // ràng buộc recipientId để không đánh dấu nhầm thông báo của người khác
    @Modifying
    @Query("update Notification n set n.read = true where n.id = :id and n.recipient.id = :recipientId")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipient.id = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);

    @Modifying
    @Query("""
            update Notification n set n.read = true
            where n.recipient.id = :recipientId and n.read = false and n.type in :types
            """)
    int markAllReadByTypes(@Param("recipientId") Long recipientId,
                           @Param("types") Collection<NotificationType> types);

    interface TypeCount {
        NotificationType getType();

        Long getTotal();
    }
}
