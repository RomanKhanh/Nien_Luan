package com.brainblocks.backend.fulltest.ops;

import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.stock.LowStockNotifier;
import com.brainblocks.backend.service.stock.LowStockPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LowStockNotifierTest {
    ProductRepository products;
    NotificationService notifications;
    LowStockNotifier notifier;
    Product product;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        notifications = mock(NotificationService.class);
        notifier = new LowStockNotifier(products, notifications, new LowStockPolicy(5));
        product = Product.builder().id(42L).name("Ops robot").build();
    }

    @Test
    void rejectsNonPositiveThresholds() {
        for (int threshold : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThatThrownBy(() -> new LowStockPolicy(threshold)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(new LowStockPolicy(1).threshold()).isEqualTo(1);
    }

    @Test
    void decreaseUsesPersistedStockInsteadOfStaleEntityAndNotifiesAtThreshold() {
        product.setStockQuantity(10);
        when(products.findStockQuantityById(42L)).thenReturn(5);
        notifier.afterDecrease(product, 1);
        verify(notifications).notifyAdmins(eq(NotificationType.LOW_STOCK), contains(product.getName()),
                contains("5"), eq("/admin/products"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void furtherDecreaseBelowThresholdDoesNotDuplicateWarning() {
        when(products.findStockQuantityById(42L)).thenReturn(4, 3);
        notifier.afterDecrease(product, 1);
        notifier.afterDecrease(product, 1);
        verifyNoInteractions(notifications);
    }

    @Test
    void restockingRearmsWarningAndReachingZeroGetsSeparateWarning() {
        notifier.afterSet(product, 6, 5);
        notifier.afterSet(product, 5, 4);
        notifier.afterSet(product, 4, 10);
        notifier.afterSet(product, 10, 4);
        notifier.afterSet(product, 4, 0);
        notifier.afterSet(product, 0, 0);
        verify(notifications, times(3)).notifyAdmins(eq(NotificationType.LOW_STOCK),
                contains(product.getName()), anyString(), eq("/admin/products"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void missingOrHiddenProductDoesNotNotify() {
        when(products.findStockQuantityById(42L)).thenReturn(null);
        notifier.afterDecrease(product, 6);
        product.setActive(false);
        when(products.findStockQuantityById(42L)).thenReturn(0);
        notifier.afterDecrease(product, 10);
        notifier.afterSet(product, 10, 0);
        verifyNoInteractions(notifications);
    }

    @Test
    void configuredThresholdControlsCrossing() {
        notifier = new LowStockNotifier(products, notifications, new LowStockPolicy(2));
        notifier.afterSet(product, 6, 3);
        verifyNoInteractions(notifications);
        notifier.afterSet(product, 3, 2);
        verify(notifications).notifyAdmins(eq(NotificationType.LOW_STOCK), anyString(),
                contains("2"), eq("/admin/products"));
    }
}
