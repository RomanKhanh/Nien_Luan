package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.util.SearchTextUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 34 tỉnh/thành sau sáp nhập năm 2025 kèm miền, và cách nhận tên tỉnh người dùng gõ tự do.
 */
public final class VietnamProvinces {

    public enum Region {
        NORTH, CENTRAL, SOUTH
    }

    public record Province(String name, Region region) {
    }

    public static final List<Province> ALL = List.of(
            new Province("Hà Nội", Region.NORTH),
            new Province("Hải Phòng", Region.NORTH),
            new Province("Quảng Ninh", Region.NORTH),
            new Province("Bắc Ninh", Region.NORTH),
            new Province("Hưng Yên", Region.NORTH),
            new Province("Ninh Bình", Region.NORTH),
            new Province("Phú Thọ", Region.NORTH),
            new Province("Thái Nguyên", Region.NORTH),
            new Province("Lạng Sơn", Region.NORTH),
            new Province("Cao Bằng", Region.NORTH),
            new Province("Tuyên Quang", Region.NORTH),
            new Province("Lào Cai", Region.NORTH),
            new Province("Lai Châu", Region.NORTH),
            new Province("Điện Biên", Region.NORTH),
            new Province("Sơn La", Region.NORTH),

            new Province("Thanh Hóa", Region.CENTRAL),
            new Province("Nghệ An", Region.CENTRAL),
            new Province("Hà Tĩnh", Region.CENTRAL),
            new Province("Huế", Region.CENTRAL),
            new Province("Quảng Trị", Region.CENTRAL),
            new Province("Đà Nẵng", Region.CENTRAL),
            new Province("Quảng Ngãi", Region.CENTRAL),
            new Province("Gia Lai", Region.CENTRAL),
            new Province("Khánh Hòa", Region.CENTRAL),
            new Province("Đắk Lắk", Region.CENTRAL),
            new Province("Lâm Đồng", Region.CENTRAL),

            new Province("TP. Hồ Chí Minh", Region.SOUTH),
            new Province("Đồng Nai", Region.SOUTH),
            new Province("Tây Ninh", Region.SOUTH),
            new Province("Đồng Tháp", Region.SOUTH),
            new Province("An Giang", Region.SOUTH),
            new Province("Vĩnh Long", Region.SOUTH),
            new Province("Cần Thơ", Region.SOUTH),
            new Province("Cà Mau", Region.SOUTH)
    );

    // tên viết tắt, tên cũ trước sáp nhập, thành phố trực thuộc hay bị gõ thay cho tỉnh -> tên chuẩn
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("hcm", "TP. Hồ Chí Minh"),
            Map.entry("tphcm", "TP. Hồ Chí Minh"),
            Map.entry("ho chi minh", "TP. Hồ Chí Minh"),
            Map.entry("sai gon", "TP. Hồ Chí Minh"),
            Map.entry("sg", "TP. Hồ Chí Minh"),
            Map.entry("ha noi", "Hà Nội"),
            Map.entry("hn", "Hà Nội"),
            Map.entry("hai phong", "Hải Phòng"),
            Map.entry("hp", "Hải Phòng"),
            Map.entry("thua thien hue", "Huế"),
            Map.entry("da lat", "Lâm Đồng"),
            Map.entry("nha trang", "Khánh Hòa"),
            Map.entry("buon ma thuot", "Đắk Lắk"),
            Map.entry("daklak", "Đắk Lắk"),
            Map.entry("vinh", "Nghệ An")
    );

    private static final Pattern PUNCTUATION = Pattern.compile("[.,\\-_]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ADMIN_PREFIX = Pattern.compile("^(tinh|thanh pho|tp)\\s+");

    private static final Map<String, Province> BY_KEY = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(p -> key(p.name()), Function.identity()));
    private static final Map<String, Province> BY_NAME = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Province::name, Function.identity()));

    private VietnamProvinces() {
    }

    /**
     * Khoá so khớp của tên tỉnh: bỏ dấu, chữ thường, dấu câu thành khoảng trắng, bỏ tiền tố "tỉnh" / "thành phố" / "TP".
     * "TP. Hồ Chí Minh" -> "ho chi minh", "Tỉnh Đắk Lắk" -> "dak lak", "TP.HCM" -> "hcm".
     */
    static String key(String name) {
        String normalized = SearchTextUtils.normalize(name);
        if (normalized == null) {
            return "";
        }
        String spaced = SPACES.matcher(PUNCTUATION.matcher(normalized).replaceAll(" ")).replaceAll(" ").trim();
        return ADMIN_PREFIX.matcher(spaced).replaceFirst("");
    }

    /**
     * Tỉnh/thành ứng với tên gõ tự do (có dấu hay không, có tiền tố hay không, bí danh như "hcm", "sài gòn").
     * Rỗng khi không nhận diện được.
     */
    public static Optional<Province> resolve(String input) {
        String key = key(input);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        String alias = ALIASES.get(key);
        if (alias != null) {
            return Optional.of(BY_NAME.get(alias));
        }
        return Optional.ofNullable(BY_KEY.get(key));
    }
}
