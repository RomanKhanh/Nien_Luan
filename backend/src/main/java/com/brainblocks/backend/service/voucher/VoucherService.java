package com.brainblocks.backend.service.voucher;

import com.brainblocks.backend.dto.response.PageResponse;
import com.brainblocks.backend.dto.response.voucher.AdminVoucherResponse;
import com.brainblocks.backend.dto.response.voucher.VoucherResponse;
import com.brainblocks.backend.dto.response.voucher.VoucherSummaryResponse;
import com.brainblocks.backend.entity.ChildProfile;
import com.brainblocks.backend.entity.Customer;
import com.brainblocks.backend.entity.Order;
import com.brainblocks.backend.entity.Voucher;
import com.brainblocks.backend.enums.NotificationType;
import com.brainblocks.backend.enums.ProductSource;
import com.brainblocks.backend.enums.VoucherReason;
import com.brainblocks.backend.enums.VoucherType;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.brainblocks.backend.repository.ChildProductRepository;
import com.brainblocks.backend.repository.ChildProductRepository.ChildSkillImpact;
import com.brainblocks.backend.repository.VoucherRepository;
import com.brainblocks.backend.security.CurrentUserProvider;
import com.brainblocks.backend.service.notification.NotificationService;
import com.brainblocks.backend.service.order.OrderService;
import com.brainblocks.backend.service.skill.SkillLevel;
import com.brainblocks.backend.service.skill.SkillScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tặng, liệt kê và áp dụng voucher (quy tắc ở VoucherPolicy). Mỗi đơn dùng tối đa 1 voucher freeship và
 * 1 voucher giảm giá (% hoặc số tiền); đơn bị hủy thì voucher được trả lại cho khách.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {
    private static final int MAX_PAGE_SIZE = 100;

    private final VoucherRepository voucherRepository;
    private final ChildProductRepository childProductRepository;
    private final VoucherPolicy policy;
    private final SkillScoreCalculator scoreCalculator;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    // ===== tặng =====

    // quà tạo tài khoản; AuthService.register gọi trong cùng transaction
    @Transactional
    public List<Voucher> grantWelcome(Customer customer) {
        return issue(customer, VoucherReason.WELCOME, null);
    }

    /**
     * Tặng voucher cho các mốc kỹ năng bé vừa đạt mà chưa được tặng. Chỉ tính đồ chơi đã mua qua đơn hàng
     * (ChildProduct PURCHASED), không tính đồ phụ huynh tự thêm, để không nhận quà chỉ bằng vài cú bấm.
     * Bé vượt nhiều mốc cùng lúc thì nhận quà của tất cả các mốc đó.
     *
     * @return các mốc vừa tặng
     */
    @Transactional
    public List<VoucherReason> grantSkillMilestones(ChildProfile child) {
        Map<Long, List<Integer>> impactsBySkill = new HashMap<>();
        for (ChildSkillImpact row : childProductRepository.findSkillImpactsBySource(child.getId(),
                ProductSource.PURCHASED)) {
            impactsBySkill.computeIfAbsent(row.getSkillId(), id -> new ArrayList<>()).add(row.getImpactIndex());
        }
        List<SkillLevel> levels = impactsBySkill.values().stream()
                .map(impacts -> SkillLevel.of(scoreCalculator.score(impacts)))
                .toList();

        Set<VoucherReason> alreadyGranted = voucherRepository.findReasonsByChildProfileId(child.getId());
        List<VoucherReason> granted = new ArrayList<>();
        for (SkillLevel level : List.of(SkillLevel.BEGINNER, SkillLevel.DEVELOPING, SkillLevel.RICH)) {
            long reached = levels.stream().filter(l -> l.compareTo(level) >= 0).count();
            VoucherReason reason = VoucherPolicy.MILESTONES.get(level);
            if (reached < VoucherPolicy.SKILLS_PER_MILESTONE || alreadyGranted.contains(reason)) {
                continue;
            }
            List<Voucher> vouchers = issue(child.getCustomer(), reason, child);
            granted.add(reason);
            notificationService.notify(child.getCustomer(), NotificationType.SKILL_PROFILE_UPDATED,
                    "Bé " + child.getName() + " đạt mốc \"" + level.label() + "\": bạn có quà",
                    VoucherPolicy.SKILLS_PER_MILESTONE + " nhóm kỹ năng của bé đã đạt mức \"" + level.label()
                            + "\". BrainBlocks tặng bạn " + vouchers.stream().map(VoucherService::label)
                            .collect(Collectors.joining(", "))
                            + " (dùng trong " + policy.validityDays() + " ngày).",
                    "/vouchers");
        }
        return granted;
    }

    private List<Voucher> issue(Customer customer, VoucherReason reason, ChildProfile child) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(policy.validityDays());
        List<Voucher> vouchers = policy.rewards(reason).stream()
                .map(spec -> Voucher.builder()
                        .customer(customer)
                        .type(spec.type())
                        .reason(reason)
                        .value(spec.value())
                        .minSubtotal(policy.minSubtotal(spec.type()))
                        .maxDiscount(policy.maxDiscount(spec.type()))
                        .childProfileId(child == null ? null : child.getId())
                        .childName(child == null ? null : child.getName())
                        .expiresAt(expiresAt)
                        .build())
                .toList();
        return voucherRepository.saveAll(vouchers);
    }

    // ===== khách xem =====

    // voucher dùng được lên trước (hết hạn sớm trước), rồi tới đã dùng / hết hạn (mới nhất trước)
    @Transactional(readOnly = true)
    public List<VoucherResponse> getMyVouchers() {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findByCustomerIdOrderByIssuedAtDesc(currentUserProvider.getCurrentUserId())
                .stream()
                .sorted(Comparator.comparing((Voucher v) -> !"AVAILABLE".equals(status(v, now)))
                        .thenComparing(v -> "AVAILABLE".equals(status(v, now)) ? v.getExpiresAt() : null,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> toResponse(v, now))
                .toList();
    }

    // ===== áp dụng khi đặt hàng =====

    /** Voucher đã chọn cho một đơn và số tiền được giảm. */
    public record Selection(Voucher shippingVoucher, Voucher discountVoucher, BigDecimal shippingDiscount,
                            BigDecimal discountAmount) {
        public static final Selection NONE = new Selection(null, null, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Kiểm tra voucher khách chọn và tính số tiền giảm. OrderService.createOrder gọi sau khi đã khóa giỏ của
     * khách, nên 2 lần đặt hàng song song của cùng khách không dùng chung được một voucher.
     */
    public Selection select(Long customerId, Long shippingVoucherId, Long discountVoucherId,
                            BigDecimal subtotal, BigDecimal shippingFee) {
        LocalDateTime now = LocalDateTime.now();
        Voucher shipping = shippingVoucherId == null ? null : usable(customerId, shippingVoucherId, subtotal, now);
        Voucher discount = discountVoucherId == null ? null : usable(customerId, discountVoucherId, subtotal, now);
        if (shipping != null && shipping.getType() != VoucherType.FREESHIP) {
            throw new IllegalArgumentException("This voucher is not a free-shipping voucher");
        }
        if (discount != null && discount.getType() == VoucherType.FREESHIP) {
            throw new IllegalArgumentException("This voucher is not a discount voucher");
        }
        return new Selection(shipping, discount,
                shipping == null ? BigDecimal.ZERO : shippingFee,
                discount == null ? BigDecimal.ZERO : discountFor(discount, subtotal));
    }

    // gắn voucher vào đơn vừa lưu
    public void markUsed(Selection selection, Order order) {
        LocalDateTime now = LocalDateTime.now();
        for (Voucher voucher : new Voucher[]{selection.shippingVoucher(), selection.discountVoucher()}) {
            if (voucher != null) {
                voucher.setOrder(order);
                voucher.setUsedAt(now);
            }
        }
    }

    // trả voucher của đơn bị hủy lại cho khách (voucher đã quá hạn thì vẫn hết hạn)
    @Transactional
    public void releaseFor(Order order) {
        for (Voucher voucher : voucherRepository.findByOrderId(order.getId())) {
            voucher.setOrder(null);
            voucher.setUsedAt(null);
        }
    }

    private Voucher usable(Long customerId, Long voucherId, BigDecimal subtotal, LocalDateTime now) {
        // voucher của khách khác trả 404 như không tồn tại
        Voucher voucher = voucherRepository.findById(voucherId)
                .filter(v -> v.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Voucher not found"));
        if (voucher.isUsed()) {
            throw new IllegalArgumentException("This voucher has already been used");
        }
        if (voucher.isExpired(now)) {
            throw new IllegalArgumentException("This voucher has expired");
        }
        if (subtotal.compareTo(voucher.getMinSubtotal()) < 0) {
            throw new IllegalArgumentException("This voucher needs a subtotal of at least "
                    + OrderService.formatMoney(voucher.getMinSubtotal()));
        }
        return voucher;
    }

    // tiền giảm trên tiền hàng: % làm tròn xuống tới đồng và không quá maxDiscount; không bao giờ quá tiền hàng
    static BigDecimal discountFor(Voucher voucher, BigDecimal subtotal) {
        BigDecimal discount = switch (voucher.getType()) {
            case PERCENT_OFF -> subtotal.multiply(voucher.getValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            case AMOUNT_OFF -> voucher.getValue();
            case FREESHIP -> BigDecimal.ZERO;
        };
        if (voucher.getMaxDiscount() != null) {
            discount = discount.min(voucher.getMaxDiscount());
        }
        return discount.min(subtotal);
    }

    // ===== admin =====

    @Transactional(readOnly = true)
    public PageResponse<AdminVoucherResponse> getVouchersForAdmin(String status, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "issuedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Voucher> vouchers = switch (status == null ? "" : status) {
            case "AVAILABLE" -> voucherRepository.findAvailable(now, pageable);
            case "USED" -> voucherRepository.findUsed(pageable);
            case "EXPIRED" -> voucherRepository.findExpired(now, pageable);
            case "" -> voucherRepository.findAllForAdmin(pageable);
            default -> throw new IllegalArgumentException("Unknown voucher status: " + status);
        };
        return PageResponse.of(vouchers, v -> new AdminVoucherResponse(toResponse(v, now),
                v.getCustomer().getId(), v.getCustomer().getFullName(), v.getCustomer().getEmail()));
    }

    @Transactional(readOnly = true)
    public VoucherSummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        return new VoucherSummaryResponse(voucherRepository.count(), voucherRepository.countAvailable(now),
                voucherRepository.countByOrderIsNotNull(), voucherRepository.countExpired(now));
    }

    // ===== hiển thị =====

    // "Miễn phí vận chuyển", "Giảm 10%", "Giảm 50.000₫"
    public static String label(Voucher voucher) {
        return switch (voucher.getType()) {
            case FREESHIP -> "Miễn phí vận chuyển";
            case PERCENT_OFF -> "Giảm " + voucher.getValue().stripTrailingZeros().toPlainString() + "%";
            case AMOUNT_OFF -> "Giảm " + OrderService.formatMoney(voucher.getValue());
        };
    }

    private static String status(Voucher voucher, LocalDateTime now) {
        if (voucher.isUsed()) {
            return "USED";
        }
        return voucher.isExpired(now) ? "EXPIRED" : "AVAILABLE";
    }

    private static VoucherResponse toResponse(Voucher v, LocalDateTime now) {
        Order order = v.getOrder();
        return new VoucherResponse(v.getId(), v.getType().name(), v.getReason().name(), label(v), v.getValue(),
                v.getMinSubtotal(), v.getMaxDiscount(), v.getChildName(), v.getIssuedAt(), v.getExpiresAt(),
                status(v, now), v.getUsedAt(), order == null ? null : order.getId(),
                order == null ? null : order.getOrderCode());
    }
}
