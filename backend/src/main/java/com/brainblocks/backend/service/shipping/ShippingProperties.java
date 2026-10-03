package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.enums.Region;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Bảng giá vận chuyển (app.shipping.* trong application.properties). Tiền tính bằng VND,
 * khối lượng bằng gram, kích thước bằng cm.
 *
 * @param volumetricDivisor  hệ số quy đổi thể tích: khối lượng quy đổi (kg) = dài x rộng x cao (cm) / hệ số
 * @param parcelMaxGrams     sức chứa một kiện, theo khối lượng tính phí
 * @param weightStepGrams    độ dài một bậc khối lượng khi tính phí
 * @param roundingStep       mọi khoản phí được làm tròn lên bội số này
 */
@Validated
@ConfigurationProperties(prefix = "app.shipping")
public record ShippingProperties(
        @Positive int volumetricDivisor,
        @Positive int parcelMaxGrams,
        @Positive int weightStepGrams,
        @NotNull @Positive BigDecimal roundingStep,
        @NotNull @Valid ZoneRates zones,
        @NotNull @Valid BulkySurcharge bulky
) {
    /**
     * Giá theo miền: bậc đầu (tới weightStepGrams) tính firstStepFee, mỗi bậc sau cộng additionalStepFee.
     */
    public record ZoneRate(
            @NotNull @PositiveOrZero BigDecimal firstStepFee,
            @NotNull @PositiveOrZero BigDecimal additionalStepFee
    ) {
    }

    // giá của từng miền giao hàng (Region của tỉnh người nhận)
    public record ZoneRates(
            @NotNull @Valid ZoneRate south,
            @NotNull @Valid ZoneRate central,
            @NotNull @Valid ZoneRate north
    ) {
        public ZoneRate of(Region region) {
            return switch (region) {
                case MIEN_NAM -> south;
                case MIEN_TRUNG -> central;
                case MIEN_BAC -> north;
            };
        }
    }

    /**
     * Phụ phí hàng cồng kềnh, tính một lần cho cả đơn khi có ít nhất một món có thể tích gói hàng
     * vượt volumeThresholdCm3 hoặc có cạnh dài hơn edgeThresholdCm.
     */
    public record BulkySurcharge(
            @NotNull @PositiveOrZero BigDecimal fee,
            @Positive long volumeThresholdCm3,
            @Positive int edgeThresholdCm
    ) {
    }
}
