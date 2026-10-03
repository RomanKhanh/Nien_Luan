package com.brainblocks.backend.util;

import java.util.stream.Stream;

public final class AddressUtils {
    private AddressUtils() {
    }

    /**
     * Địa chỉ đầy đủ để hiển thị: "Số 1 Lý Tự Trọng, Phường Cái Khế, Quận Ninh Kiều, Thành phố Cần Thơ".
     * Phần trống (vd huyện đảo không có cấp xã) được bỏ qua.
     */
    public static String format(String detail, String wardName, String districtName, String provinceName) {
        return String.join(", ", Stream.of(detail, wardName, districtName, provinceName)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .toList());
    }
}
