package com.brainblocks.backend.dto.response.chat;

import java.time.LocalDate;
import java.util.List;

public record ChatStatsResponse(long totalSessions, long totalQuestions, long questionsLast7Days,
        double avgResponseTimeMs, List<TopicStat> topTopics, List<QuestionStat> topQuestions,
        List<ProductStat> topSuggestedProducts, List<DailyStat> dailyQuestions) {
    public record TopicStat(String topic, long total) {}
    public record QuestionStat(String question, long total) {}
    public record ProductStat(Long productId, String productName, long total) {}
    public record DailyStat(LocalDate date, long total) {}
}
