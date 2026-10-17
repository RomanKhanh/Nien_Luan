package com.brainblocks.backend.util;

/**
 * Quy đổi giữa độ tương đồng cosine (cấu hình dùng, 1 = giống hệt) và khoảng cách cosine của pgvector
 * (toán tử {@code <=>}, 0 = giống hệt): distance = 1 - similarity.
 */
public final class VectorMath {
    private VectorMath() {}

    public static double toDistance(double similarity) { return 1 - similarity; }

    public static double toSimilarity(double distance) { return 1 - distance; }

    // Dạng chữ "[0.1,0.2,...]" mà pgvector đọc được qua CAST(? AS vector).
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) throw new IllegalArgumentException("Vector must not be empty");
        StringBuilder text = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) throw new IllegalArgumentException("Vector contains a non-finite value");
            if (i > 0) text.append(',');
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
