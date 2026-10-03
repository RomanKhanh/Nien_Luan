package com.brainblocks.backend.service.shipping;

/**
 * Khu vực giao hàng, suy ra từ tỉnh/thành người nhận so với tỉnh đặt kho.
 */
public enum ShippingZone {
    INTRA_PROVINCE("Nội tỉnh"),
    INTRA_REGION("Nội miền"),
    INTER_REGION("Liên miền");

    private final String label;

    ShippingZone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
