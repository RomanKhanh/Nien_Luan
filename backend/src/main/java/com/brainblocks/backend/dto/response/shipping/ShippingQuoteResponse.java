package com.brainblocks.backend.dto.response.shipping;

import java.math.BigDecimal;
import java.util.List;

/**
 * Báo giá phí vận chuyển giỏ hàng hiện tại tới một tỉnh.
 * zone = MIEN_NAM / MIEN_TRUNG / MIEN_BAC, zoneLabel = "Miền Nam"...; khối lượng tính bằng gram.
 */
public record ShippingQuoteResponse(
        int provinceCode,
        String provinceName,
        String zone,
        String zoneLabel,
        long actualWeightGrams,
        long volumetricWeightGrams,
        long chargeableWeightGrams,
        int parcelCount,
        List<ParcelResponse> parcels,
        BigDecimal baseFee,
        BigDecimal bulkySurcharge,
        BigDecimal totalFee,
        List<String> warnings
) {
    public record ParcelResponse(
            List<ParcelContentResponse> contents,
            long actualWeightGrams,
            long volumetricWeightGrams,
            long chargeableWeightGrams,
            boolean oversized,
            BigDecimal baseFee
    ) {
    }

    public record ParcelContentResponse(Long productId, String productName, int quantity) {
    }
}
