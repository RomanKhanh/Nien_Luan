package com.brainblocks.backend.service.shipping;

import com.brainblocks.backend.entity.Product;

/**
 * Một dòng hàng cần giao: thông số gói hàng sau đóng gói của sản phẩm (Product.weightGrams / lengthCm / widthCm /
 * heightCm) và số lượng.
 */
public record ShippingItem(
        Long productId,
        String name,
        int weightGrams,
        int lengthCm,
        int widthCm,
        int heightCm,
        int quantity
) {
    public ShippingItem {
        if (weightGrams <= 0 || lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("Shipping weight and dimensions must be positive: " + name);
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Shipping quantity must be at least 1: " + name);
        }
    }

    public static ShippingItem of(Product product, int quantity) {
        return new ShippingItem(product.getId(), product.getName(), product.getWeightGrams(),
                product.getLengthCm(), product.getWidthCm(), product.getHeightCm(), quantity);
    }

    // thể tích một gói, cm³ (long: 200 x 200 x 200 đã là 8 triệu, nhân thêm số lượng dễ tràn int)
    public long volumeCm3() {
        return (long) lengthCm * widthCm * heightCm;
    }

    public int longestEdgeCm() {
        return Math.max(lengthCm, Math.max(widthCm, heightCm));
    }
}
