package com.brainblocks.backend.service.stock;


import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.repository.NotificationRepository;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LowStockNotifier {
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final LowStockPolicy policy;

    public void afterDecrease(Product product, int quantity){
        Integer after = productRepository.findStockQuantityById(product.getId());
        if(after != null){
            notifyIfCrossed(product, after + quantity, after);
        }
    }

    public void afterSet(Product product, int before, int after){
        notifyIfCrossed(product, before, after);
    }

    private void notifyIfCrossed(Product product, int before, int after){
        if(!product.isActive()){
            return;
        }
        int threshold = policy.threshold();
        if(after == 0 && before > 0){
            notificationService.notifyAdmins(NotificationType.LOW_STOCK,
                    "Hết hàng: " + product.getName(),
                    "Sản phẩm \"" + product.getName() + "\" vừa hết hàng, khách không đặt được nữa cho tới khi nhập thêm.",
                    "/admin/products");
        } else if (after > 0 && after <= threshold && before > threshold) {
            notificationService.notifyAdmins(NotificationType.LOW_STOCK,
                    "Sắp hết hàng: " + product.getName(),
                    "Sản phẩm \"" + product.getName() + "\" chỉ còn " + after + " (ngưỡng cảnh báo " + threshold + ").",
                    "/admin/products");
        }
    }
}
