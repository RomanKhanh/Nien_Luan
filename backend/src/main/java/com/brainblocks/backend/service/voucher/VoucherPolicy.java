package com.brainblocks.backend.service.voucher;

import com.brainblocks.backend.enums.VoucherReason;
import com.brainblocks.backend.enums.VoucherType;
import com.brainblocks.backend.service.skill.SkillLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Quy tắc tặng voucher và điều kiện dùng (cấu hình app.voucher.*):
 * <ul>
 *   <li>Tạo tài khoản: 1 freeship, 1 giảm 10%, 1 giảm 50.000₫.</li>
 *   <li>Mỗi bé, khi 2 nhóm kỹ năng bất kỳ đạt "Mới bắt đầu": 1 giảm 10%; đạt "Đang phát triển": 1 giảm 20% +
 *       1 freeship; đạt "Phong phú": 1 giảm 20% + 1 giảm 100.000₫ + 1 freeship. Mỗi mốc tặng 1 lần cho mỗi bé.</li>
 *   <li>Freeship và giảm %: tiền hàng (chưa gồm phí ship) từ freeship-min-subtotal / percent-min-subtotal;
 *       giảm % tối đa percent-max-discount. Giảm theo số tiền: không có mức tối thiểu.</li>
 *   <li>Hạn dùng validity-days ngày kể từ lúc nhận.</li>
 * </ul>
 */
@Component
public class VoucherPolicy {
    // số nhóm kỹ năng phải cùng đạt một mức thì mới tính là đạt mốc
    public static final int SKILLS_PER_MILESTONE = 2;

    // mức kỹ năng -> lý do tặng tương ứng, theo thứ tự từ thấp lên cao
    public static final Map<SkillLevel, VoucherReason> MILESTONES = Map.of(
            SkillLevel.BEGINNER, VoucherReason.SKILL_BEGINNER,
            SkillLevel.DEVELOPING, VoucherReason.SKILL_DEVELOPING,
            SkillLevel.RICH, VoucherReason.SKILL_RICH);

    public record Spec(VoucherType type, BigDecimal value) {}

    private static final Map<VoucherReason, List<Spec>> REWARDS = Map.of(
            VoucherReason.WELCOME, List.of(freeship(), percent(10), amount(50_000)),
            VoucherReason.SKILL_BEGINNER, List.of(percent(10)),
            VoucherReason.SKILL_DEVELOPING, List.of(percent(20), freeship()),
            VoucherReason.SKILL_RICH, List.of(percent(20), amount(100_000), freeship()));

    private final int validityDays;
    private final BigDecimal freeshipMinSubtotal;
    private final BigDecimal percentMinSubtotal;
    private final BigDecimal percentMaxDiscount;

    public VoucherPolicy(@Value("${app.voucher.validity-days:30}") int validityDays,
                         @Value("${app.voucher.freeship-min-subtotal:200000}") BigDecimal freeshipMinSubtotal,
                         @Value("${app.voucher.percent-min-subtotal:200000}") BigDecimal percentMinSubtotal,
                         @Value("${app.voucher.percent-max-discount:150000}") BigDecimal percentMaxDiscount) {
        if (validityDays < 1) {
            throw new IllegalStateException("app.voucher.validity-days must be >= 1, got " + validityDays);
        }
        this.validityDays = validityDays;
        this.freeshipMinSubtotal = freeshipMinSubtotal;
        this.percentMinSubtotal = percentMinSubtotal;
        this.percentMaxDiscount = percentMaxDiscount;
    }

    public int validityDays() {
        return validityDays;
    }

    public List<Spec> rewards(VoucherReason reason) {
        return REWARDS.get(reason);
    }

    public BigDecimal minSubtotal(VoucherType type) {
        return switch (type) {
            case FREESHIP -> freeshipMinSubtotal;
            case PERCENT_OFF -> percentMinSubtotal;
            case AMOUNT_OFF -> BigDecimal.ZERO;
        };
    }

    // null = không giới hạn
    public BigDecimal maxDiscount(VoucherType type) {
        return type == VoucherType.PERCENT_OFF ? percentMaxDiscount : null;
    }

    private static Spec freeship() {
        return new Spec(VoucherType.FREESHIP, null);
    }

    private static Spec percent(int percent) {
        return new Spec(VoucherType.PERCENT_OFF, BigDecimal.valueOf(percent));
    }

    private static Spec amount(long amount) {
        return new Spec(VoucherType.AMOUNT_OFF, BigDecimal.valueOf(amount));
    }
}
