package com.brainblocks.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@Slf4j
public class KnowledgeAsyncConfig {
    // Worker riêng: xử lý tài liệu không chiếm luồng HTTP trả lời chat.
    @Bean("knowledgeExecutor")
    public ThreadPoolTaskExecutor knowledgeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("knowledge-");
        return executor;
    }

    // Embed sản phẩm (ProductEmbeddingService): tách khỏi knowledgeExecutor để sửa sản phẩm không phải chờ sau
    // việc index tài liệu dài. 1 luồng để các lượt cập nhật cùng sản phẩm chạy tuần tự. Hàng đợi đầy thì bỏ qua
    // và ghi log (không ném lỗi về request admin đã lưu xong); lần khởi động sau backfill bù lại theo hash.
    @Bean("productEmbeddingExecutor")
    public ThreadPoolTaskExecutor productEmbeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("product-embedding-");
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Product embedding queue is full; task skipped"));
        return executor;
    }
}
