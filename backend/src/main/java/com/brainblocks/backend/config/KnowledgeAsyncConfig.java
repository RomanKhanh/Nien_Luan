package com.brainblocks.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
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
}
