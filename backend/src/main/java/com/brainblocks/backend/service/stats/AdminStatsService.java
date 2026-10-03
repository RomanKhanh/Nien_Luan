package com.brainblocks.backend.service.stats;

import com.brainblocks.backend.dto.response.stats.AdminStatsResponse;
import com.brainblocks.backend.dto.response.stats.AdminStatsResponse.DailyOrderStat;
import com.brainblocks.backend.dto.response.stats.AdminStatsResponse.SkillInterestStat;
import com.brainblocks.backend.dto.response.stats.AdminStatsResponse.TopProductStat;
import com.brainblocks.backend.dto.response.stats.AdminStatsResponse.TopicStat;
import com.brainblocks.backend.enums.ComplaintStatus;
import com.brainblocks.backend.enums.OrderStatus;
import com.brainblocks.backend.enums.SenderType;
import com.brainblocks.backend.repository.ChatMessageRepository;
import com.brainblocks.backend.repository.ComplaintRepository;
import com.brainblocks.backend.repository.CustomerRepository;
import com.brainblocks.backend.repository.OrderItemRepository;
import com.brainblocks.backend.repository.OrderRepository;
import com.brainblocks.backend.repository.OrderRepository.OrderAmount;
import com.brainblocks.backend.repository.ProductRepository;
import com.brainblocks.backend.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// số liệu dashboard quản trị (đề 2.14); mỗi chỉ số là một câu aggregate, không load danh sách thực thể
@Service
@RequiredArgsConstructor
public class AdminStatsService {
    // tồn kho <= mức này thì coi là sắp hết
    public static final int LOW_STOCK_THRESHOLD = 5;
    private static final int DAILY_RANGE_DAYS = 14;
    private static final int TOP_LIMIT = 5;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SkillRepository skillRepository;
    private final ComplaintRepository complaintRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        LocalDate today = LocalDate.now();

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status.name(), 0L);
        }
        orderRepository.countByStatus().forEach(row -> ordersByStatus.put(row.getStatus().name(), row.getTotal()));
        long totalOrders = ordersByStatus.values().stream().mapToLong(Long::longValue).sum();

        List<TopProductStat> topProducts = orderItemRepository
                .findTopSelling(OrderStatus.CANCELLED, PageRequest.of(0, TOP_LIMIT)).stream()
                .map(row -> new TopProductStat(row.getProductId(), row.getProductName(),
                        row.getQuantity(), row.getRevenue()))
                .toList();

        List<SkillInterestStat> skillInterests = skillRepository.countInterestedChildren().stream()
                .map(row -> new SkillInterestStat(row.getSkillId(), row.getSkillName(), row.getTotal()))
                .sorted(Comparator.comparingLong(SkillInterestStat::children).reversed())
                .toList();

        List<TopicStat> chatTopics = chatMessageRepository.countByTopic(PageRequest.of(0, TOP_LIMIT)).stream()
                .map(row -> new TopicStat(row.getTopic(), row.getTotal()))
                .toList();

        return new AdminStatsResponse(
                customerRepository.count(),
                customerRepository.countByCreatedAtGreaterThanEqual(today.minusDays(30).atStartOfDay()),
                productRepository.countByActiveTrue(),
                productRepository.countByActiveTrueAndStockQuantityLessThanEqual(LOW_STOCK_THRESHOLD),
                totalOrders,
                ordersByStatus,
                orderRepository.sumSubtotalByStatus(OrderStatus.DELIVERED),
                orderRepository.sumShippingFeeByStatus(OrderStatus.DELIVERED),
                dailyOrders(today),
                topProducts,
                skillInterests,
                complaintRepository.countByStatus(ComplaintStatus.PENDING),
                chatMessageRepository.countBySender(SenderType.USER),
                chatTopics);
    }

    // số đơn và tiền hàng (không gồm phí ship, không tính đơn hủy) theo từng ngày trong 14 ngày gần nhất, ngày không có đơn = 0
    private List<DailyOrderStat> dailyOrders(LocalDate today) {
        LocalDate from = today.minusDays(DAILY_RANGE_DAYS - 1);
        Map<LocalDate, long[]> counts = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> amounts = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            counts.put(d, new long[]{0});
            amounts.put(d, BigDecimal.ZERO);
        }
        for (OrderAmount row : orderRepository.findAmountsSince(from.atStartOfDay(), OrderStatus.CANCELLED)) {
            LocalDate day = row.getCreatedAt().toLocalDate();
            if (counts.containsKey(day)) {
                counts.get(day)[0]++;
                amounts.merge(day, row.getSubtotal(), BigDecimal::add);
            }
        }
        List<DailyOrderStat> result = new ArrayList<>();
        counts.forEach((day, count) -> result.add(new DailyOrderStat(day, count[0], amounts.get(day))));
        return result;
    }
}
