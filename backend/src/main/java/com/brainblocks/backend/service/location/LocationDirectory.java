package com.brainblocks.backend.service.location;

import com.brainblocks.backend.enums.Region;
import com.brainblocks.backend.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Địa chính 63 tỉnh/thành TRƯỚC sáp nhập 07/2025: tỉnh -> quận/huyện -> phường/xã, kèm miền giao hàng của tỉnh.
 * Đọc một lần từ locations/vn-locations.json (tạo bằng scripts/fetch-vn-locations.mjs), lúc chạy không gọi API ngoài.
 */
@Component
public class LocationDirectory {
    static final String RESOURCE = "locations/vn-locations.json";

    public record Ward(int code, String name, int districtCode) {
    }

    // một số huyện đảo (Bạch Long Vĩ, Cồn Cỏ, Hoàng Sa, Lý Sơn, Côn Đảo) không có cấp xã: wards rỗng
    public record District(int code, String name, int provinceCode, List<Ward> wards) {
        public boolean hasWards() {
            return !wards.isEmpty();
        }
    }

    public record Province(int code, String name, Region region, List<District> districts) {
    }

    /**
     * Địa chỉ đã kiểm tra đủ quan hệ cha-con; ward null khi huyện không có cấp xã.
     */
    public record ResolvedAddress(Province province, District district, Ward ward) {
    }

    // cấu trúc của file JSON
    private record WardData(int code, String name) {
    }

    private record DistrictData(int code, String name, List<WardData> wards) {
    }

    private record ProvinceData(int code, String name, List<DistrictData> districts) {
    }

    private final List<Province> provinces;
    private final Map<Integer, Province> provinceByCode = new HashMap<>();
    private final Map<Integer, District> districtByCode = new HashMap<>();
    private final Map<Integer, Ward> wardByCode = new HashMap<>();

    public LocationDirectory() {
        List<ProvinceData> data;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            data = List.of(new ObjectMapper().readValue(in, ProvinceData[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        List<Province> loaded = new ArrayList<>();
        for (ProvinceData p : data) {
            List<District> districts = new ArrayList<>();
            for (DistrictData d : p.districts()) {
                List<Ward> wards = d.wards().stream().map(w -> new Ward(w.code(), w.name(), d.code())).toList();
                District district = new District(d.code(), d.name(), p.code(), wards);
                districts.add(district);
                districtByCode.put(district.code(), district);
                wards.forEach(w -> wardByCode.put(w.code(), w));
            }
            Province province = new Province(p.code(), p.name(), Region.ofProvince(p.code()), List.copyOf(districts));
            loaded.add(province);
            provinceByCode.put(province.code(), province);
        }
        this.provinces = List.copyOf(loaded);
    }

    public List<Province> provinces() {
        return provinces;
    }

    public Optional<Province> province(Integer code) {
        return Optional.ofNullable(code == null ? null : provinceByCode.get(code));
    }

    public Optional<District> district(Integer code) {
        return Optional.ofNullable(code == null ? null : districtByCode.get(code));
    }

    public Optional<Ward> ward(Integer code) {
        return Optional.ofNullable(code == null ? null : wardByCode.get(code));
    }

    public Province requireProvince(Integer code) {
        return province(code).orElseThrow(() -> new ResourceNotFoundException("Province not found"));
    }

    public District requireDistrict(Integer code) {
        return district(code).orElseThrow(() -> new ResourceNotFoundException("District not found"));
    }

    /**
     * Kiểm tra bộ mã tỉnh / huyện / xã người dùng gửi lên: phải tồn tại và đúng quan hệ cha-con.
     * Huyện có cấp xã thì bắt buộc chọn xã; huyện không có cấp xã thì không được gửi mã xã.
     *
     * @throws IllegalArgumentException (400) kèm lý do cụ thể
     */
    public ResolvedAddress resolve(Integer provinceCode, Integer districtCode, Integer wardCode) {
        Province province = province(provinceCode)
                .orElseThrow(() -> new IllegalArgumentException("Province code is invalid"));
        District district = district(districtCode)
                .orElseThrow(() -> new IllegalArgumentException("District code is invalid"));
        if (district.provinceCode() != province.code()) {
            throw new IllegalArgumentException("District does not belong to the selected province");
        }
        if (!district.hasWards()) {
            if (wardCode != null) {
                throw new IllegalArgumentException("This district has no wards");
            }
            return new ResolvedAddress(province, district, null);
        }
        if (wardCode == null) {
            throw new IllegalArgumentException("Ward is required");
        }
        Ward ward = ward(wardCode).orElseThrow(() -> new IllegalArgumentException("Ward code is invalid"));
        if (ward.districtCode() != district.code()) {
            throw new IllegalArgumentException("Ward does not belong to the selected district");
        }
        return new ResolvedAddress(province, district, ward);
    }
}
