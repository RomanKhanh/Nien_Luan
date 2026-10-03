package com.brainblocks.backend.enums;

import java.util.Set;

/**
 * Miền của 63 tỉnh/thành (mã theo provinces.open-api.vn v1, trước sáp nhập 07/2025), dùng làm vùng giao hàng.
 * NAM = Đông Nam Bộ + Đồng bằng sông Cửu Long; TRUNG = Bắc Trung Bộ + Duyên hải Nam Trung Bộ + Tây Nguyên;
 * BAC = các tỉnh còn lại.
 */
public enum Region {
    MIEN_NAM("Miền Nam"),
    MIEN_TRUNG("Miền Trung"),
    MIEN_BAC("Miền Bắc");

    // Đông Nam Bộ: Bình Phước, Tây Ninh, Bình Dương, Đồng Nai, Bà Rịa - Vũng Tàu, TP. Hồ Chí Minh
    // ĐBSCL: Long An, Tiền Giang, Bến Tre, Trà Vinh, Vĩnh Long, Đồng Tháp, An Giang, Kiên Giang, Cần Thơ,
    //        Hậu Giang, Sóc Trăng, Bạc Liêu, Cà Mau
    private static final Set<Integer> SOUTH = Set.of(
            70, 72, 74, 75, 77, 79,
            80, 82, 83, 84, 86, 87, 89, 91, 92, 93, 94, 95, 96);

    // Bắc Trung Bộ: Thanh Hóa, Nghệ An, Hà Tĩnh, Quảng Bình, Quảng Trị, Huế
    // Duyên hải Nam Trung Bộ: Đà Nẵng, Quảng Nam, Quảng Ngãi, Bình Định, Phú Yên, Khánh Hòa, Ninh Thuận, Bình Thuận
    // Tây Nguyên: Kon Tum, Gia Lai, Đắk Lắk, Đắk Nông, Lâm Đồng
    private static final Set<Integer> CENTRAL = Set.of(
            38, 40, 42, 44, 45, 46,
            48, 49, 51, 52, 54, 56, 58, 60,
            62, 64, 66, 67, 68);

    // còn lại (mã 1 - 37): Đồng bằng sông Hồng, Đông Bắc, Tây Bắc
    private static final int LAST_NORTH_CODE = 37;

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Region ofProvince(int provinceCode) {
        if (SOUTH.contains(provinceCode)) {
            return MIEN_NAM;
        }
        if (CENTRAL.contains(provinceCode)) {
            return MIEN_TRUNG;
        }
        if (provinceCode >= 1 && provinceCode <= LAST_NORTH_CODE) {
            return MIEN_BAC;
        }
        throw new IllegalArgumentException("Unknown province code: " + provinceCode);
    }
}
