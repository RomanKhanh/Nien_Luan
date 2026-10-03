package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.enums.Region;
import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả tính phí vận chuyển một đơn.
 *
 * @param zone                    miền giao hàng của tỉnh người nhận
 * @param actualWeightGrams       tổng khối lượng thực của cả đơn
 * @param volumetricWeightGrams   tổng khối lượng quy đổi từ thể tích của cả đơn (làm tròn lên gram)
 * @param chargeableWeightGrams   max(thực, quy đổi) của cả đơn; phí lại tính theo từng kiện, xem parcels
 * @param parcels                 các kiện sau khi xếp bằng First Fit Decreasing
 * @param baseFee                 tổng phí cơ bản của các kiện
 * @param bulkySurcharge          phụ phí hàng cồng kềnh, tối đa một lần cho cả đơn
 * @param totalFee                baseFee + bulkySurcharge
 * @param warnings                cảnh báo cho người dùng (món quá khổ...)
 */
public record ShippingQuote(
        Region zone,
        long actualWeightGrams,
        long volumetricWeightGrams,
        long chargeableWeightGrams,
        List<Parcel> parcels,
        BigDecimal baseFee,
        BigDecimal bulkySurcharge,
        BigDecimal totalFee,
        List<String> warnings
) {
    public int parcelCount() {
        return parcels.size();
    }

    // có kiện chứa một món tự nó đã vượt sức chứa kiện
    public boolean hasOversizedParcel() {
        return parcels.stream().anyMatch(Parcel::oversized);
    }

    /**
     * Một kiện hàng. Khối lượng tính phí = max(tổng thực, tổng thể tích / hệ số), tính trên tổng của cả kiện.
     *
     * @param oversized kiện chỉ có một món và món đó tự nó vượt sức chứa kiện
     * @param baseFee   phí cơ bản của riêng kiện này
     */
    public record Parcel(
            List<Content> contents,
            long actualWeightGrams,
            long volumeCm3,
            long volumetricWeightGrams,
            long chargeableWeightGrams,
            boolean oversized,
            BigDecimal baseFee
    ) {
        public int unitCount() {
            return contents.stream().mapToInt(Content::quantity).sum();
        }
    }

    // số món của một sản phẩm nằm trong kiện
    public record Content(Long productId, String name, int quantity) {
    }
}
