package com.brainblocks.backend.service.location;

import com.brainblocks.backend.enums.Region;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dữ liệu địa chính đọc từ vn-locations.json và bảng phân miền của 63 tỉnh. Unit test thuần, không cần Spring.
 */
class LocationDirectoryTest {
    private final LocationDirectory locations = new LocationDirectory();

    @Test
    void loadsAllSixtyThreeProvincesWithDistrictsAndWards() {
        assertThat(locations.provinces()).hasSize(63);
        assertThat(locations.provinces()).allSatisfy(p -> assertThat(p.districts()).isNotEmpty());
        assertThat(locations.provinces().stream().mapToLong(p -> p.districts().size()).sum()).isEqualTo(696);
        assertThat(locations.province(92)).get().extracting(LocationDirectory.Province::name)
                .isEqualTo("Thành phố Cần Thơ");
        assertThat(locations.district(916)).get().extracting(LocationDirectory.District::provinceCode).isEqualTo(92);
        assertThat(locations.ward(31117)).get().extracting(LocationDirectory.Ward::districtCode).isEqualTo(916);
    }

    // mọi tỉnh trong dữ liệu đều có miền; Nam 19, Trung 19, Bắc 25
    @Test
    void everyProvinceHasARegion() {
        Map<Region, Long> counts = locations.provinces().stream()
                .collect(Collectors.groupingBy(LocationDirectory.Province::region, Collectors.counting()));
        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
                Map.of(Region.MIEN_NAM, 19L, Region.MIEN_TRUNG, 19L, Region.MIEN_BAC, 25L));
    }

    @Test
    void regionOfTypicalProvinces() {
        assertThat(Region.ofProvince(92)).isEqualTo(Region.MIEN_NAM);   // Cần Thơ
        assertThat(Region.ofProvince(79)).isEqualTo(Region.MIEN_NAM);   // TP. Hồ Chí Minh
        assertThat(Region.ofProvince(48)).isEqualTo(Region.MIEN_TRUNG); // Đà Nẵng
        assertThat(Region.ofProvince(68)).isEqualTo(Region.MIEN_TRUNG); // Lâm Đồng
        assertThat(Region.ofProvince(38)).isEqualTo(Region.MIEN_TRUNG); // Thanh Hóa
        assertThat(Region.ofProvince(1)).isEqualTo(Region.MIEN_BAC);    // Hà Nội
        assertThat(Region.MIEN_TRUNG.label()).isEqualTo("Miền Trung");
        assertThatThrownBy(() -> Region.ofProvince(999)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveChecksParentChildRelations() {
        LocationDirectory.ResolvedAddress ok = locations.resolve(92, 916, 31117);
        assertThat(ok.ward().name()).isEqualTo("Phường Cái Khế");
        assertThat(locations.resolve(77, 755, null).ward()).isNull(); // Côn Đảo không có cấp xã

        assertThatThrownBy(() -> locations.resolve(92, 760, 26734)).hasMessage(
                "District does not belong to the selected province");
        assertThatThrownBy(() -> locations.resolve(92, 916, 31153)).hasMessage(
                "Ward does not belong to the selected district");
        assertThatThrownBy(() -> locations.resolve(null, 916, 31117)).hasMessage("Province code is invalid");
        assertThatThrownBy(() -> locations.resolve(92, 916, null)).hasMessage("Ward is required");
    }
}
