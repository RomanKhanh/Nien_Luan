package com.brainblocks.backend.dto.response.order;

import java.math.BigDecimal;

// childProfileId / childName: bé được mua cho (chọn ở bước checkout); null nếu không gắn bé nào
public record OrderItemResponse(Long id, Long productId, String productName,
                                int quantity, BigDecimal unitPrice, BigDecimal subtotal,
                                Long childProfileId, String childName) {}
