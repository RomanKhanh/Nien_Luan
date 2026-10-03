package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.service.shipping.ShippingProperties.BulkySurcharge;
import com.brainblocks.backend.service.shipping.ShippingProperties.ZoneRate;
import com.brainblocks.backend.service.shipping.ShippingProperties.ZoneRates;
import com.brainblocks.backend.service.shipping.ShippingQuote.Parcel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Bộ tính phí vận chuyển, unit test thuần không cần Spring. Bảng giá giống application.properties:
 * kho Cần Thơ, nội tỉnh 15.000 + 2.500 / 0,5 kg, nội miền 22.000 + 4.000, liên miền 30.000 + 5.500,
 * hệ số quy đổi 6000, kiện tối đa 20 kg, cồng kềnh 20.000 khi thể tích > 30.000 cm³ hoặc cạnh > 100 cm.
 */
class ShippingCalculatorTest {
    private static final String WAREHOUSE = "Cần Thơ";

    // Đối chứng với project cũ (green-garden-ship, src/utils/__tests__/xepKien.test.js): cùng dữ liệu cây cảnh
    // trong products.js, đổi kg -> gram. Kim Tiền 25 x 25 x 40 = 25.000 cm³ -> quy đổi 4,1667 kg > thực 1,5 kg.
    private static final ShippingItem KIM_TIEN = item(1L, "Cây Kim Tiền", 1500, 25, 25, 40);
    private static final ShippingItem LUOI_HO = item(2L, "Lưỡi Hổ", 1800, 20, 20, 50);
    private static final ShippingItem TRAU_BA_XANH = item(3L, "Trầu Bà Xanh", 800, 20, 20, 25);
    // 40 x 40 x 90 = 144.000 cm³ -> 24 kg quy đổi: tự nó vượt sức chứa kiện, và cồng kềnh theo thể tích
    private static final ShippingItem BANG_SINGAPORE = item(4L, "Bàng Singapore", 5000, 40, 40, 90);
    private static final ShippingItem SEN_DA_NAU = item(5L, "Sen Đá Nâu", 300, 10, 10, 10);
    private static final ShippingItem LAN_Y = item(7L, "Lan Ý", 1200, 22, 22, 35);
    // 35 x 35 x 70 = 85.750 cm³ -> 14,2917 kg quy đổi, hai cây không ghép chung một kiện được
    private static final ShippingItem CO_NHAT = item(11L, "Cọ Nhật", 3500, 35, 35, 70);
    private static final ShippingItem VAN_NIEN_THANH = item(12L, "Vạn Niên Thanh", 2800, 30, 30, 60);

    private final ShippingCalculator calculator = new ShippingCalculator(defaults());

    static ShippingProperties defaults() {
        return withRates(WAREHOUSE, rates(15000, 2500, 22000, 4000, 30000, 5500), 500);
    }

    static ShippingProperties withRates(String warehouse, ZoneRates zones, int roundingStep) {
        return new ShippingProperties(warehouse, 6000, 20_000, 500, money(roundingStep), zones,
                new BulkySurcharge(money(20000), 30_000, 100));
    }

    static ZoneRates rates(int localFirst, int localNext, int regionFirst, int regionNext, int farFirst, int farNext) {
        return new ZoneRates(new ZoneRate(money(localFirst), money(localNext)),
                new ZoneRate(money(regionFirst), money(regionNext)), new ZoneRate(money(farFirst), money(farNext)));
    }

    static BigDecimal money(long value) {
        return BigDecimal.valueOf(value);
    }

    static ShippingItem item(Long id, String name, int grams, int length, int width, int height) {
        return new ShippingItem(id, name, grams, length, width, height, 1);
    }

    static ShippingItem times(ShippingItem item, int quantity) {
        return new ShippingItem(item.productId(), item.name(), item.weightGrams(), item.lengthCm(), item.widthCm(),
                item.heightCm(), quantity);
    }

    // món nhỏ gọn: 10 x 10 x 10 = 1.000 cm³ (quy đổi 166,7 g) nên khối lượng thực luôn thắng
    static ShippingItem compact(int grams) {
        return item(100L, "Hộp nhỏ " + grams + " g", grams, 10, 10, 10);
    }

    private ShippingQuote quote(ShippingItem... items) {
        return calculator.quote(WAREHOUSE, List.of(items));
    }

    private static void assertMoney(BigDecimal actual, long expected) {
        assertThat(actual).isEqualByComparingTo(money(expected));
    }

    @Nested
    class EmptyOrder {
        @Test
        void emptyOrderCostsNothing() {
            ShippingQuote q = calculator.quote("Hà Nội", List.of());
            assertThat(q.parcelCount()).isZero();
            assertThat(q.parcels()).isEmpty();
            assertThat(q.actualWeightGrams()).isZero();
            assertThat(q.chargeableWeightGrams()).isZero();
            assertMoney(q.baseFee(), 0);
            assertMoney(q.bulkySurcharge(), 0);
            assertMoney(q.totalFee(), 0);
            assertThat(q.warnings()).isEmpty();
            assertThat(q.zone()).isEqualTo(ShippingZone.INTER_REGION);
        }

        // có hàng thì dù nhẹ cỡ nào cũng thu đủ phí bậc đầu
        @Test
        void lightestItemStillPaysFirstStep() {
            assertMoney(quote(compact(1)).totalFee(), 15000);
        }
    }

    @Nested
    class WeightSteps {
        // bậc 0,5 kg: tới 500 g là bậc đầu, 501 g sang bậc 2, 1.000 g vẫn bậc 2, 1.001 g sang bậc 3
        @Test
        void halfKilogramBoundaries() {
            assertMoney(quote(compact(500)).baseFee(), 15000);
            assertMoney(quote(compact(501)).baseFee(), 17500);
            assertMoney(quote(compact(1000)).baseFee(), 17500);
            assertMoney(quote(compact(1001)).baseFee(), 20000);
        }

        // ranh giới theo thể tích: 3.000 cm³ quy đổi đúng 500 g, 3.100 cm³ là 516,7 g
        @Test
        void volumetricBoundaryUsesExactArithmetic() {
            ShippingQuote exact = quote(item(1L, "Hộp 3000", 100, 30, 10, 10));
            assertThat(exact.chargeableWeightGrams()).isEqualTo(500);
            assertMoney(exact.baseFee(), 15000);

            ShippingQuote over = quote(item(2L, "Hộp 3100", 100, 31, 10, 10));
            assertThat(over.volumetricWeightGrams()).isEqualTo(517);
            assertMoney(over.baseFee(), 17500);
        }

        @Test
        void eachZoneUsesItsOwnRates() {
            ShippingItem kilo = compact(1000); // 2 bậc
            assertMoney(calculator.quote("Cần Thơ", List.of(kilo)).baseFee(), 15000 + 2500);
            assertMoney(calculator.quote("An Giang", List.of(kilo)).baseFee(), 22000 + 4000);
            assertMoney(calculator.quote("Hà Nội", List.of(kilo)).baseFee(), 30000 + 5500);
        }

        // đồ chơi 650 g, hộp 30 x 22 x 8 cm (5.280 cm³ -> 880 g quy đổi): tính theo 880 g, 2 bậc
        @Test
        void typicalToyIsChargedByVolumetricWeight() {
            ShippingQuote q = calculator.quote("Hà Nội", List.of(item(1L, "Robot dò đường", 650, 30, 22, 8)));
            assertThat(q.actualWeightGrams()).isEqualTo(650);
            assertThat(q.volumetricWeightGrams()).isEqualTo(880);
            assertThat(q.chargeableWeightGrams()).isEqualTo(880);
            assertMoney(q.totalFee(), 35500);
        }
    }

    @Nested
    class LightButBulky {
        // gấu bông 200 g, hộp 40 x 30 x 30 = 36.000 cm³ -> 6 kg quy đổi (12 bậc) và cồng kềnh theo thể tích
        @Test
        void volumetricWeightWinsAndBulkySurchargeApplies() {
            ShippingQuote q = quote(item(1L, "Gấu bông", 200, 40, 30, 30));
            assertThat(q.actualWeightGrams()).isEqualTo(200);
            assertThat(q.volumetricWeightGrams()).isEqualTo(6000);
            assertThat(q.chargeableWeightGrams()).isEqualTo(6000);
            assertMoney(q.baseFee(), 15000 + 11 * 2500);
            assertMoney(q.bulkySurcharge(), 20000);
            assertMoney(q.totalFee(), 62500);
        }

        // thể tích nhỏ nhưng có cạnh dài hơn 100 cm vẫn là cồng kềnh; đúng 100 cm thì chưa
        @Test
        void longEdgeIsBulky() {
            assertMoney(quote(item(1L, "Thước dài", 300, 101, 5, 5)).bulkySurcharge(), 20000);
            assertMoney(quote(item(2L, "Thước 1m", 300, 100, 5, 5)).bulkySurcharge(), 0);
            assertMoney(quote(item(3L, "Hộp 30000", 300, 30, 50, 20)).bulkySurcharge(), 0);
        }

        // nhiều món cồng kềnh, nhiều kiện: phụ phí vẫn chỉ tính một lần
        @Test
        void bulkySurchargeIsChargedOncePerOrder() {
            ShippingQuote q = quote(times(BANG_SINGAPORE, 3), times(CO_NHAT, 2),
                    item(9L, "Thước dài", 300, 120, 5, 5));
            assertThat(q.parcelCount()).isGreaterThan(1);
            assertMoney(q.bulkySurcharge(), 20000);
            assertThat(q.totalFee()).isEqualByComparingTo(q.baseFee().add(q.bulkySurcharge()));
        }
    }

    @Nested
    class Packing {
        // ca 1 project cũ
        @Test
        void singleKimTienIsOneParcelChargedByVolume() {
            ShippingQuote q = quote(KIM_TIEN);
            assertThat(q.parcelCount()).isEqualTo(1);
            Parcel parcel = q.parcels().getFirst();
            assertThat(parcel.actualWeightGrams()).isEqualTo(1500);
            assertThat(parcel.volumeCm3()).isEqualTo(25_000);
            assertThat(parcel.chargeableWeightGrams()).isEqualTo(4167); // 4,1667 kg làm tròn lên gram
            assertThat(parcel.oversized()).isFalse();
            assertMoney(parcel.baseFee(), 15000 + 8 * 2500); // 9 bậc
        }

        // ca 3: số lượng 3 được bung thành 3 món khi xếp
        @Test
        void quantityIsExpandedIntoUnits() {
            ShippingQuote q = quote(times(TRAU_BA_XANH, 3));
            assertThat(q.parcels().stream().mapToInt(Parcel::unitCount).sum()).isEqualTo(3);
            assertThat(q.actualWeightGrams()).isEqualTo(2400);
        }

        // ca 4: mọi kiện không vượt 20 kg, trừ kiện chỉ chứa đúng một món tự nó quá khổ
        @Test
        void everyParcelRespectsCapacityExceptSingleOversizedUnit() {
            ShippingQuote q = quote(times(BANG_SINGAPORE, 3), times(CO_NHAT, 2), times(KIM_TIEN, 4),
                    times(VAN_NIEN_THANH, 3), times(TRAU_BA_XANH, 5));
            assertThat(q.parcels()).isNotEmpty();
            for (Parcel parcel : q.parcels()) {
                boolean valid = parcel.chargeableWeightGrams() <= 20_000
                        || (parcel.unitCount() == 1 && parcel.oversized());
                assertThat(valid).as(parcel.toString()).isTrue();
            }
        }

        // ca 5, 6: Bàng Singapore 24 kg quy đổi -> mỗi cây một kiện riêng, đánh dấu quá khổ
        @Test
        void oversizedUnitGetsItsOwnParcel() {
            ShippingQuote one = quote(BANG_SINGAPORE);
            assertThat(one.parcelCount()).isEqualTo(1);
            assertThat(one.parcels().getFirst().chargeableWeightGrams()).isEqualTo(24_000);
            assertThat(one.parcels().getFirst().oversized()).isTrue();
            assertThat(one.hasOversizedParcel()).isTrue();

            ShippingQuote three = quote(times(BANG_SINGAPORE, 3));
            assertThat(three.parcelCount()).isEqualTo(3);
            assertThat(three.parcels()).allSatisfy(p -> {
                assertThat(p.unitCount()).isEqualTo(1);
                assertThat(p.oversized()).isTrue();
            });
        }

        // ca 7: 2 Cọ Nhật 14,29 kg -> ghép đôi 28,58 > 20 nên 2 kiện, không kiện nào quá khổ
        @Test
        void twoUnitsThatCannotShareAParcel() {
            ShippingQuote q = quote(times(CO_NHAT, 2));
            assertThat(q.parcelCount()).isEqualTo(2);
            assertThat(q.parcels()).allSatisfy(p -> {
                assertThat(p.unitCount()).isEqualTo(1);
                assertThat(p.chargeableWeightGrams()).isEqualTo(14_292); // 14.291,67 g
                assertThat(p.oversized()).isFalse();
            });
        }

        // ca 8: khối lượng tính phí của kiện = max(tổng thực, tổng thể tích / 6000), không phải tổng max từng món
        @Test
        void parcelChargeableWeightUsesParcelTotals() {
            ShippingQuote q = quote(KIM_TIEN, SEN_DA_NAU);
            assertThat(q.parcelCount()).isEqualTo(1);
            Parcel parcel = q.parcels().getFirst();
            assertThat(parcel.actualWeightGrams()).isEqualTo(1800);
            assertThat(parcel.volumeCm3()).isEqualTo(26_000);
            assertThat(parcel.chargeableWeightGrams()).isEqualTo(4334); // 4.333,33 g
            // cộng max từng món: 4.166,67 + 300 = 4.466,67 g, sai
            assertThat(parcel.chargeableWeightGrams()).isLessThan(4467);
        }

        // ca 10: Cọ Nhật x2, Kim Tiền x2, Cọ Nhật x2, Kim Tiền x2 -> FFD ra 4 kiện (Next Fit ra 5)
        @Test
        void ffdPacksInterleavedCartIntoFourParcels() {
            ShippingQuote q = quote(times(CO_NHAT, 2), times(KIM_TIEN, 2), times(CO_NHAT, 2), times(KIM_TIEN, 2));
            assertThat(q.parcelCount()).isEqualTo(4);
        }

        // ca 10b: FFD là heuristic, ca này ra 3 kiện dù xếp khéo được 2
        @Test
        void ffdIsHeuristicNotOptimal() {
            ShippingQuote q = quote(times(KIM_TIEN, 4), times(LAN_Y, 3), CO_NHAT);
            assertThat(q.parcelCount()).isEqualTo(3);
        }

        // ca 11: bảo toàn tổng số món và tổng khối lượng thực qua các kiện
        @Test
        void packingPreservesUnitsAndWeight() {
            ShippingQuote q = quote(times(CO_NHAT, 2), times(KIM_TIEN, 2), times(CO_NHAT, 2), times(KIM_TIEN, 2));
            assertThat(q.parcels().stream().mapToInt(Parcel::unitCount).sum()).isEqualTo(8);
            assertThat(q.parcels().stream().mapToLong(Parcel::actualWeightGrams).sum())
                    .isEqualTo(q.actualWeightGrams())
                    .isEqualTo(4 * 3500 + 4 * 1500);
        }

        // ca 13, 16: phí cơ bản là tổng phí từng kiện, tổng phí = phí cơ bản + phụ phí cồng kềnh
        @Test
        void baseFeeIsSumOfParcelFees() {
            ShippingQuote q = quote(times(BANG_SINGAPORE, 3), times(CO_NHAT, 2));
            assertThat(q.parcelCount()).isEqualTo(5);
            BigDecimal sum = q.parcels().stream().map(Parcel::baseFee).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(q.baseFee()).isEqualByComparingTo(sum);
            // 3 kiện 24 kg (48 bậc) + 2 kiện 14,29 kg (29 bậc), nội tỉnh
            assertMoney(q.baseFee(), 3 * (15000 + 47 * 2500) + 2 * (15000 + 28 * 2500));
            assertThat(q.totalFee()).isEqualByComparingTo(q.baseFee().add(q.bulkySurcharge()));
        }

        // khối lượng của cả đơn là max(tổng thực, tổng quy đổi), độc lập với cách chia kiện
        @Test
        void orderWeightsAreOrderTotals() {
            ShippingQuote q = quote(times(KIM_TIEN, 2), SEN_DA_NAU);
            assertThat(q.actualWeightGrams()).isEqualTo(3300);
            assertThat(q.volumetricWeightGrams()).isEqualTo(8500); // 51.000 cm³ / 6
            assertThat(q.chargeableWeightGrams()).isEqualTo(8500);
        }

        // ca 19: đơn nặng hơn 20 kg tự tách kiện hợp lệ, không có cảnh báo
        @Test
        void heavyOrderIsSplitWithoutWarnings() {
            ShippingQuote q = quote(times(KIM_TIEN, 10)); // 41,67 kg quy đổi, 4 cây / kiện
            assertThat(q.parcelCount()).isEqualTo(3);
            assertThat(q.parcels()).extracting(Parcel::unitCount).containsExactly(4, 4, 2);
            assertThat(q.hasOversizedParcel()).isFalse();
            assertThat(q.warnings()).isEmpty();
        }

        @Test
        void parcelContentsGroupUnitsByProduct() {
            ShippingQuote q = quote(times(SEN_DA_NAU, 3), times(TRAU_BA_XANH, 2));
            assertThat(q.parcelCount()).isEqualTo(1);
            assertThat(q.parcels().getFirst().contents())
                    .extracting(ShippingQuote.Content::name, ShippingQuote.Content::quantity)
                    .containsExactlyInAnyOrder(
                            tuple("Trầu Bà Xanh", 2),
                            tuple("Sen Đá Nâu", 3));
        }
    }

    @Nested
    class OversizedWarnings {
        // nặng hơn sức chứa kiện nhưng nhỏ gọn: không cồng kềnh
        private final ShippingItem heavyButCompact = item(9001L, "Chậu Xi Măng Đặc", 25_000, 20, 20, 20);

        // ca 21: quá khổ vì khối lượng thực, không cồng kềnh -> cảnh báo không nhắc phụ phí
        @Test
        void oversizedWithoutBulkyDoesNotMentionSurcharge() {
            ShippingQuote q = quote(heavyButCompact);
            assertThat(q.hasOversizedParcel()).isTrue();
            assertMoney(q.bulkySurcharge(), 0);
            assertThat(q.warnings()).singleElement().satisfies(w -> {
                assertThat(w).contains("Chậu Xi Măng Đặc", "25 kg", "20 kg", "kiện riêng");
                assertThat(w).doesNotContain("phụ phí");
            });
        }

        // ca 22: có thêm món cồng kềnh thật -> có phụ phí và cảnh báo mới nhắc tới
        @Test
        void oversizedWithBulkyMentionsSurcharge() {
            ShippingQuote q = quote(heavyButCompact, BANG_SINGAPORE);
            assertMoney(q.bulkySurcharge(), 20000);
            assertThat(q.warnings()).hasSize(2).allSatisfy(w -> assertThat(w).contains("phụ phí hàng cồng kềnh"));
        }

        // mua nhiều cái cùng một món quá khổ chỉ cảnh báo một lần
        @Test
        void oneWarningPerOversizedProduct() {
            ShippingQuote q = quote(times(BANG_SINGAPORE, 3));
            assertThat(q.parcelCount()).isEqualTo(3);
            assertThat(q.warnings()).singleElement().asString().contains("Bàng Singapore", "24 kg");
        }
    }

    @Nested
    class Zones {
        @Test
        void zoneFollowsWarehouseProvinceAndRegion() {
            assertThat(calculator.quote("Cần Thơ", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTRA_PROVINCE);
            assertThat(calculator.quote("Cà Mau", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTRA_REGION);
            assertThat(calculator.quote("TP. Hồ Chí Minh", List.of(KIM_TIEN)).zone())
                    .isEqualTo(ShippingZone.INTRA_REGION);
            assertThat(calculator.quote("Đà Nẵng", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTER_REGION);
            assertThat(calculator.quote("Hà Nội", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTER_REGION);
        }

        // tỉnh lạ / bỏ trống -> khu vực đắt nhất, kèm cảnh báo, không có tên tỉnh chuẩn
        @Test
        void unknownProvinceFallsBackToMostExpensiveZone() {
            for (String unknown : new String[]{"Atlantis", "", "   ", null}) {
                ShippingQuote q = calculator.quote(unknown, List.of(compact(500)));
                assertThat(q.zone()).as(unknown).isEqualTo(ShippingZone.INTER_REGION);
                assertThat(q.province()).isNull();
                assertMoney(q.totalFee(), 30000);
                assertThat(q.warnings()).singleElement().asString().contains("Liên miền");
            }
            assertThat(calculator.quote("Atlantis", List.of()).warnings().getFirst()).contains("\"Atlantis\"");
        }

        // "đắt nhất" đọc theo bảng giá, không cố định là liên miền
        @Test
        void mostExpensiveZoneFollowsConfiguredRates() {
            ShippingCalculator odd = new ShippingCalculator(
                    withRates(WAREHOUSE, rates(15000, 2500, 40000, 1000, 30000, 5500), 500));
            assertThat(odd.quote("??", List.of(compact(500))).zone()).isEqualTo(ShippingZone.INTRA_REGION);
        }

        @Test
        void aliasesResolveToCanonicalName() {
            for (String alias : new String[]{"hcm", "HCM", "tphcm", "TP.HCM", "Tp. HCM", "Sài Gòn", "sai gon", "SG",
                    "Thành phố Hồ Chí Minh", "ho chi minh"}) {
                ShippingQuote q = calculator.quote(alias, List.of(KIM_TIEN));
                assertThat(q.province()).as(alias).isEqualTo("TP. Hồ Chí Minh");
                assertThat(q.zone()).isEqualTo(ShippingZone.INTRA_REGION);
                assertThat(q.warnings()).isEmpty();
            }
            assertThat(calculator.quote("Thừa Thiên Huế", List.of()).province()).isEqualTo("Huế");
            assertThat(calculator.quote("Buôn Ma Thuột", List.of()).province()).isEqualTo("Đắk Lắk");
            assertThat(calculator.quote("daklak", List.of()).province()).isEqualTo("Đắk Lắk");
            assertThat(calculator.quote("Nha Trang", List.of()).province()).isEqualTo("Khánh Hòa");
            assertThat(calculator.quote("hn", List.of()).province()).isEqualTo("Hà Nội");
        }

        // bỏ dấu, hoa thường, khoảng trắng thừa, tiền tố "tỉnh" / "thành phố" / "TP"
        @Test
        void provinceNamesAreNormalized() {
            for (String input : new String[]{"Cần Thơ", "can tho", "  CẦN   THƠ ", "Thành phố Cần Thơ", "TP Cần Thơ",
                    "tp. can tho"}) {
                ShippingQuote q = calculator.quote(input, List.of(KIM_TIEN));
                assertThat(q.province()).as(input).isEqualTo("Cần Thơ");
                assertThat(q.zone()).isEqualTo(ShippingZone.INTRA_PROVINCE);
            }
            assertThat(calculator.quote("Tỉnh Đắk Lắk", List.of()).province()).isEqualTo("Đắk Lắk");
            assertThat(calculator.quote("tinh thanh hoa", List.of()).province()).isEqualTo("Thanh Hóa");
        }

        @Test
        void thirtyFourProvincesEachResolveToThemselves() {
            assertThat(VietnamProvinces.ALL).hasSize(34);
            assertThat(VietnamProvinces.ALL).extracting(VietnamProvinces.Province::region)
                    .filteredOn(r -> r == VietnamProvinces.Region.SOUTH).hasSize(8);
            for (VietnamProvinces.Province p : VietnamProvinces.ALL) {
                assertThat(VietnamProvinces.resolve(p.name())).as(p.name()).contains(p);
            }
        }
    }

    @Nested
    class Configuration {
        // phí cấu hình lẻ vẫn được làm tròn lên bội 500đ, kể cả phụ phí cồng kềnh
        @Test
        void feesAreRoundedUpToRoundingStep() {
            ShippingProperties props = new ShippingProperties(WAREHOUSE, 6000, 20_000, 500, money(500),
                    rates(15100, 2600, 22000, 4000, 30000, 5500), new BulkySurcharge(money(20001), 30_000, 100));
            ShippingCalculator odd = new ShippingCalculator(props);
            assertMoney(odd.quote("Cần Thơ", List.of(compact(500))).baseFee(), 15500);
            assertMoney(odd.quote("Cần Thơ", List.of(compact(1000))).baseFee(), 18000); // 17.700
            assertMoney(odd.quote("Cần Thơ", List.of(item(1L, "Gấu bông", 200, 40, 30, 30))).bulkySurcharge(),
                    20500);
        }

        @Test
        void warehouseProvinceMustBeKnown() {
            assertThatThrownBy(() -> new ShippingCalculator(withRates("Atlantis", rates(1, 1, 1, 1, 1, 1), 500)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Atlantis");
        }

        // kho đặt ở nơi khác thì khu vực đổi theo
        @Test
        void warehouseCanBeMoved() {
            ShippingCalculator hanoi = new ShippingCalculator(
                    withRates("hn", rates(15000, 2500, 22000, 4000, 30000, 5500), 500));
            assertThat(hanoi.quote("Hà Nội", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTRA_PROVINCE);
            assertThat(hanoi.quote("Hải Phòng", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTRA_REGION);
            assertThat(hanoi.quote("Cần Thơ", List.of(KIM_TIEN)).zone()).isEqualTo(ShippingZone.INTER_REGION);
        }

        @Test
        void invalidItemsAreRejected() {
            assertThatThrownBy(() -> new ShippingItem(1L, "x", 500, 10, 10, 10, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ShippingItem(1L, "x", 0, 10, 10, 10, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ShippingItem(1L, "x", 500, 10, -1, 10, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
