package com.brainblocks.backend.config;

import com.brainblocks.backend.entity.Product;
import com.brainblocks.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Sản phẩm tạo trước khi có cột search_text thì chưa có giá trị: điền một lần lúc khởi động
// để tìm kiếm không dấu ra cả sản phẩm cũ. Sản phẩm mới tự điền qua Product.syncSearchText.
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchTextBackfillRunner implements CommandLineRunner {
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Product> missing = productRepository.findBySearchTextIsNull();
        missing.forEach(Product::syncSearchText);
        if (!missing.isEmpty()) {
            log.info("Filled search text for {} products", missing.size());
        }
    }
}
