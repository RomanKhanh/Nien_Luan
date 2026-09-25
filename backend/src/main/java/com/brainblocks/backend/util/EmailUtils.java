package com.brainblocks.backend.util;

import java.util.Locale;

public final class EmailUtils {
    private EmailUtils() {
    }

    // Email lưu và so sánh ở dạng chữ thường để Bao@gmail.com và bao@gmail.com là một tài khoản.
    // Locale.ROOT để kết quả không phụ thuộc ngôn ngữ máy chạy (vd tiếng Thổ: "I" -> "ı").
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
