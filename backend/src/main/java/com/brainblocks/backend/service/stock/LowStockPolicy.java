package com.brainblocks.backend.service.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LowStockPolicy {
    private final int threshold;

    public LowStockPolicy(@Value("${app.stock.low-threshold:5}") int threshold){
        if(threshold < 1) {
            throw new IllegalStateException("app.stock.low-threshold must be >= 1, got " + threshold);
        }
        this.threshold = threshold;
    }

    public int threshold(){
        return threshold;
    }
}
