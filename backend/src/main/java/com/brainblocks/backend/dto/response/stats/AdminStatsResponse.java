package com.brainblocks.backend.dto.response.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// số liệu cho dashboard quản trị (đề 2.14)
public record AdminStatsResponse(
        long totalCustomers,
        long newCustomersLast30Days,
        long activeProducts,
        long lowStockProducts,
        long totalOrders,
        // số đơn theo từng trạng thái; trạng thái chưa có đơn nào = 0
        Map<String, Long> ordersByStatus,
        // doanh thu = tổng tiền hàng các đơn đã giao, KHÔNG gồm phí vận chuyển
        BigDecimal deliveredRevenue,
        // tổng phí vận chuyển đã thu của các đơn đã giao, tách riêng khỏi doanh thu
        BigDecimal deliveredShippingFees,
        List<DailyOrderStat> dailyOrders,
        List<TopProductStat> topProducts,
        List<SkillInterestStat> skillInterests,
        long pendingComplaints,
        long chatQuestions,
        List<TopicStat> chatTopics
) {
    public record DailyOrderStat(LocalDate date, long orders, BigDecimal amount) {
    }

    public record TopProductStat(Long productId, String productName, long quantity, BigDecimal revenue) {
    }

    public record SkillInterestStat(Long skillId, String skillName, long children) {
    }

    public record TopicStat(String topic, long total) {
    }
}
