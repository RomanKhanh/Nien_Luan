package com.brainblocks.backend.controller.location;

import com.brainblocks.backend.dto.response.ApiResponse;
import com.brainblocks.backend.dto.response.location.DistrictResponse;
import com.brainblocks.backend.dto.response.location.LocationResponse;
import com.brainblocks.backend.dto.response.location.ProvinceResponse;
import com.brainblocks.backend.service.location.LocationDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

// địa chính 63 tỉnh/thành (trước sáp nhập 07/2025) cho dropdown địa chỉ, mở công khai (SecurityConfig).
// Dữ liệu cố định trong bộ nhớ nên cho trình duyệt cache 1 ngày.
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private static final CacheControl CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();

    private final LocationDirectory locations;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> provinces() {
        return cached(locations.provinces().stream()
                .map(p -> new ProvinceResponse(p.code(), p.name(), p.region().name(), p.region().label()))
                .toList());
    }

    @GetMapping("/provinces/{code}/districts")
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> districts(@PathVariable int code) {
        return cached(locations.requireProvince(code).districts().stream()
                .map(d -> new DistrictResponse(d.code(), d.name(), d.hasWards()))
                .toList());
    }

    @GetMapping("/districts/{code}/wards")
    public ResponseEntity<ApiResponse<List<LocationResponse>>> wards(@PathVariable int code) {
        return cached(locations.requireDistrict(code).wards().stream()
                .map(w -> new LocationResponse(w.code(), w.name()))
                .toList());
    }

    private static <T> ResponseEntity<ApiResponse<T>> cached(T data) {
        return ResponseEntity.ok().cacheControl(CACHE).body(ApiResponse.success(data));
    }
}
