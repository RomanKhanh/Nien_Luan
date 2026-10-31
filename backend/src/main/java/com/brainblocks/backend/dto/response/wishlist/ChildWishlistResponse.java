package com.brainblocks.backend.dto.response.wishlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChildWishlistResponse(Long id,
                                    Long productId,
                                    String productName,
                                    BigDecimal price,
                                    int minAge,
                                    int maxAge,
                                    boolean active,
                                    boolean inStock,
                                    boolean ageAppropriate,
                                    boolean alreadyOwned,
                                    LocalDateTime createdAt) {
}
