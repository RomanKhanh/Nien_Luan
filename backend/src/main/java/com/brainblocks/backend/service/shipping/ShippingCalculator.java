package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.service.shipping.ShippingQuote.Content;
import com.brainblocks.backend.service.shipping.ShippingQuote.Parcel;
import com.brainblocks.backend.service.shipping.VietnamProvinces.Province;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tính phí vận chuyển một đơn hàng từ kho (app.shipping.warehouse-province) tới tỉnh người nhận.
 *
 * <ol>
 *   <li>Khu vực: cùng tỉnh kho = nội tỉnh, cùng miền = nội miền, còn lại = liên miền.
 *       Tỉnh không nhận diện được tính theo khu vực đắt nhất để không thu thiếu.</li>
 *   <li>Xếp kiện bằng First Fit Decreasing: bung từng món theo số lượng, sắp giảm dần theo khối lượng tính phí,
 *       đặt mỗi món vào kiện đầu tiên còn chứa được (khối lượng tính phí của cả kiện không vượt parcelMaxGrams),
 *       không có thì mở kiện mới. Món tự nó vượt sức chứa thành kiện riêng, đánh dấu oversized.</li>
 *   <li>Khối lượng tính phí của một kiện = max(tổng khối lượng thực, tổng thể tích / hệ số quy đổi), tính trên
 *       tổng của cả kiện chứ không cộng max của từng món.</li>
 *   <li>Phí cơ bản tính riêng từng kiện theo bậc weightStepGrams rồi cộng lại.</li>
 *   <li>Phụ phí cồng kềnh tính một lần cho cả đơn nếu có món nào vượt ngưỡng thể tích hoặc cạnh dài.</li>
 * </ol>
 *
 * Mọi phép so sánh khối lượng dùng số nguyên quy về đơn vị "gram x hệ số quy đổi" để không lệch vì số lẻ:
 * khối lượng quy đổi của V cm³ là V x 1000 / hệ số gram, nhân cả hai vế với hệ số thì thành V x 1000.
 */
@Component
public class ShippingCalculator {
    private static final long GRAMS_PER_KG = 1000;

    private final ShippingProperties props;
    private final Province warehouse;

    public ShippingCalculator(ShippingProperties props) {
        this.props = props;
        this.warehouse = VietnamProvinces.resolve(props.warehouseProvince())
                .orElseThrow(() -> new IllegalStateException(
                        "app.shipping.warehouse-province is not a known province: " + props.warehouseProvince()));
    }

    public ShippingQuote quote(String recipientProvince, List<ShippingItem> items) {
        List<String> warnings = new ArrayList<>();
        Optional<Province> province = VietnamProvinces.resolve(recipientProvince);
        ShippingZone zone = province.map(this::zoneOf).orElseGet(() -> {
            ShippingZone fallback = mostExpensiveZone();
            warnings.add(recipientProvince == null || recipientProvince.isBlank()
                    ? "Chưa có tỉnh/thành người nhận, tạm tính theo khu vực " + fallback.label() + "."
                    : "Không nhận diện được tỉnh/thành \"" + recipientProvince.trim() + "\", tạm tính theo khu vực "
                    + fallback.label() + ".");
            return fallback;
        });

        long actualGrams = 0;
        long volumeCm3 = 0;
        boolean bulky = false;
        for (ShippingItem item : items) {
            actualGrams += (long) item.weightGrams() * item.quantity();
            volumeCm3 += item.volumeCm3() * item.quantity();
            bulky |= isBulky(item);
        }
        long volumetricGrams = Math.ceilDiv(volumeCm3 * GRAMS_PER_KG, props.volumetricDivisor());

        List<Parcel> parcels = pack(items).stream().map(bin -> bin.toParcel(zone)).toList();
        BigDecimal baseFee = parcels.stream().map(Parcel::baseFee).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal bulkySurcharge = bulky ? roundUp(props.bulky().fee()) : BigDecimal.ZERO;
        warnings.addAll(oversizedWarnings(parcels, bulkySurcharge.signum() > 0));

        return new ShippingQuote(zone, province.map(Province::name).orElse(null), actualGrams, volumetricGrams,
                Math.max(actualGrams, volumetricGrams), parcels, baseFee, bulkySurcharge,
                baseFee.add(bulkySurcharge), List.copyOf(warnings));
    }

    public ShippingZone zoneOf(Province recipient) {
        if (recipient.equals(warehouse)) {
            return ShippingZone.INTRA_PROVINCE;
        }
        return recipient.region() == warehouse.region() ? ShippingZone.INTRA_REGION : ShippingZone.INTER_REGION;
    }

    // phí cơ bản của một kiện theo khối lượng tính phí (đơn vị gram x hệ số): bậc đầu + mỗi bậc thêm
    BigDecimal baseFee(ShippingZone zone, long chargeableScaled) {
        if (chargeableScaled <= 0) {
            return BigDecimal.ZERO;
        }
        long steps = Math.ceilDiv(chargeableScaled, (long) props.weightStepGrams() * props.volumetricDivisor());
        ShippingProperties.ZoneRate rate = props.zones().of(zone);
        return roundUp(rate.firstStepFee().add(rate.additionalStepFee().multiply(BigDecimal.valueOf(steps - 1))));
    }

    // First Fit Decreasing
    private List<Bin> pack(List<ShippingItem> items) {
        List<ShippingItem> units = new ArrayList<>();
        for (ShippingItem item : items) {
            for (int i = 0; i < item.quantity(); i++) {
                units.add(item);
            }
        }
        // sort ổn định: món nặng bằng nhau giữ thứ tự trong giỏ
        units.sort(Comparator.comparingLong(this::chargeableScaled).reversed());

        long capacity = (long) props.parcelMaxGrams() * props.volumetricDivisor();
        List<Bin> bins = new ArrayList<>();
        for (ShippingItem unit : units) {
            Bin target = null;
            for (Bin bin : bins) {
                if (bin.chargeableScaledWith(unit) <= capacity) {
                    target = bin;
                    break;
                }
            }
            if (target == null) {
                target = new Bin();
                bins.add(target);
            }
            target.add(unit);
        }
        return bins;
    }

    // khối lượng tính phí của một món, đơn vị gram x hệ số quy đổi
    private long chargeableScaled(ShippingItem unit) {
        return Math.max((long) unit.weightGrams() * props.volumetricDivisor(), unit.volumeCm3() * GRAMS_PER_KG);
    }

    private boolean isBulky(ShippingItem item) {
        return item.volumeCm3() > props.bulky().volumeThresholdCm3()
                || item.longestEdgeCm() > props.bulky().edgeThresholdCm();
    }

    // khu vực có phí bậc đầu cao nhất (bằng nhau thì xét phí mỗi bậc thêm)
    private ShippingZone mostExpensiveZone() {
        Comparator<ShippingZone> byFee = Comparator
                .comparing((ShippingZone z) -> props.zones().of(z).firstStepFee())
                .thenComparing(z -> props.zones().of(z).additionalStepFee());
        return List.of(ShippingZone.values()).stream().max(byFee).orElseThrow();
    }

    // mỗi sản phẩm quá khổ chỉ cảnh báo một lần dù mua nhiều cái
    private List<String> oversizedWarnings(List<Parcel> parcels, boolean bulkyCharged) {
        List<String> warnings = new ArrayList<>();
        Set<ContentKey> warned = new HashSet<>();
        for (Parcel parcel : parcels) {
            if (!parcel.oversized()) {
                continue;
            }
            Content content = parcel.contents().getFirst();
            if (!warned.add(new ContentKey(content.productId(), content.name()))) {
                continue;
            }
            warnings.add("Sản phẩm '" + content.name() + "' có khối lượng tính phí "
                    + formatKg(parcel.chargeableWeightGrams()) + ", vượt sức chứa một kiện ("
                    + formatKg(props.parcelMaxGrams()) + ") nên được đóng thành kiện riêng."
                    + (bulkyCharged ? " Đơn hàng đã được tính phụ phí hàng cồng kềnh." : ""));
        }
        return warnings;
    }

    private BigDecimal roundUp(BigDecimal amount) {
        BigDecimal step = props.roundingStep();
        return amount.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    // 4167 -> "4,17 kg", 20000 -> "20 kg"
    static String formatKg(long grams) {
        return BigDecimal.valueOf(grams).divide(BigDecimal.valueOf(GRAMS_PER_KG), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',') + " kg";
    }

    private record ContentKey(Long productId, String name) {
    }

    // kiện đang xếp: giữ sẵn tổng để thử thêm một món không phải cộng lại cả kiện
    private final class Bin {
        private final Map<ContentKey, Content> contents = new LinkedHashMap<>();
        private long actualGrams;
        private long volumeCm3;
        private int units;

        long chargeableScaledWith(ShippingItem unit) {
            return chargeableScaled(actualGrams + unit.weightGrams(), volumeCm3 + unit.volumeCm3());
        }

        void add(ShippingItem unit) {
            actualGrams += unit.weightGrams();
            volumeCm3 += unit.volumeCm3();
            units++;
            contents.merge(new ContentKey(unit.productId(), unit.name()),
                    new Content(unit.productId(), unit.name(), 1),
                    (a, b) -> new Content(a.productId(), a.name(), a.quantity() + 1));
        }

        Parcel toParcel(ShippingZone zone) {
            long scaled = chargeableScaled(actualGrams, volumeCm3);
            long volumetricGrams = Math.ceilDiv(volumeCm3 * GRAMS_PER_KG, props.volumetricDivisor());
            boolean oversized = units == 1 && scaled > (long) props.parcelMaxGrams() * props.volumetricDivisor();
            return new Parcel(List.copyOf(contents.values()), actualGrams, volumeCm3, volumetricGrams,
                    Math.ceilDiv(scaled, props.volumetricDivisor()), oversized, baseFee(zone, scaled));
        }

        private long chargeableScaled(long grams, long volume) {
            return Math.max(grams * props.volumetricDivisor(), volume * GRAMS_PER_KG);
        }
    }
}
