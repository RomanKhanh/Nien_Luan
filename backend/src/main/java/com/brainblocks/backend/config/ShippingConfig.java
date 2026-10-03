package com.brainblocks.backend.config;

import com.brainblocks.backend.service.shipping.ShippingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// bật bảng giá vận chuyển app.shipping.* (ShippingProperties)
@Configuration
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfig {
}
