package com.brainblocks.backend.config;

import com.brainblocks.backend.service.ai.ProductEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// Sản phẩm có từ trước khi có product_embeddings (hoặc đổi lúc Gemini lỗi) chưa có vector đúng: lúc khởi động,
// embed bù các sản phẩm chưa có / lỗi thời theo lô 50, chạy nền nên không làm chậm khởi động.
// Sản phẩm đã đúng hash thì không gọi Gemini. Tắt bằng PRODUCT_EMBEDDING_BACKFILL=false.
@Component
@RequiredArgsConstructor
public class ProductEmbeddingBackfillRunner implements CommandLineRunner {
    private final ProductEmbeddingService productEmbeddings;

    @Value("${app.product-embedding.backfill-on-startup:true}")
    private boolean enabled = true;

    @Override
    public void run(String... args) {
        if (enabled) productEmbeddings.backfillAsync();
    }
}
