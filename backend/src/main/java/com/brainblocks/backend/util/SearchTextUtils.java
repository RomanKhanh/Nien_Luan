package com.brainblocks.backend.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SearchTextUtils {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchTextUtils() {
    }

    // Bỏ dấu tiếng Việt, chữ thường, gộp khoảng trắng: "Lắp  Ráp Đồ" -> "lap rap do".
    // Dùng cho cả dữ liệu lưu (Product.searchText) lẫn từ khóa người dùng gõ, để gõ không dấu vẫn tìm ra.
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D');
        return SPACES.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
