package com.brainblocks.backend.fulltest.ops;

import com.brainblocks.backend.entity.Admin;
import com.brainblocks.backend.entity.Notification;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.repository.AdminRepository;
import com.brainblocks.backend.repository.NotificationRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void broadcastsSeparateUnreadCopiesToEnabledAdminsOnly() {
        NotificationRepository repository = mock(NotificationRepository.class);
        AdminRepository admins = mock(AdminRepository.class);
        Admin first = Admin.builder().id(1L).enabled(true).build();
        Admin second = Admin.builder().id(2L).enabled(true).build();
        Admin disabled = Admin.builder().id(3L).enabled(false).build();
        when(admins.findAll()).thenReturn(List.of(first, disabled, second));
        List<Notification> saved = new ArrayList<>();
        when(repository.saveAll(anyList())).thenAnswer(call -> {
            List<Notification> batch = call.getArgument(0);
            saved.addAll(batch);
            return batch;
        });
        NotificationService service = new NotificationService(repository, admins, mock(CurrentUserProvider.class));
        service.notifyAdmins(NotificationType.LOW_STOCK, "Ops low stock", "Only 2 remain", "/admin/products");
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Notification::getRecipient).containsExactly(first, second);
        assertThat(saved).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.LOW_STOCK);
            assertThat(notification.getTitle()).isEqualTo("Ops low stock");
            assertThat(notification.getMessage()).isEqualTo("Only 2 remain");
            assertThat(notification.getLink()).isEqualTo("/admin/products");
            assertThat(notification.isRead()).isFalse();
        });
        saved.getFirst().setRead(true);
        assertThat(saved.get(1).isRead()).isFalse();
        verify(repository).saveAll(anyList());
    }
}
