package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.CartItemAnalytics;

import java.time.Instant;

public record CartItemAnalyticsResponse(
        Long cartItemId,
        Long cartId,
        Long productId,
        String productName,
        int quantity,
        boolean active,
        Instant addedAt,
        Instant updatedAt,
        Instant removedAt
) {
    public static CartItemAnalyticsResponse from(CartItemAnalytics e) {
        return new CartItemAnalyticsResponse(
                e.getCartItemId(), e.getCartId(), e.getProductId(), e.getProductName(),
                e.getQuantity(), e.isActive(), e.getAddedAt(), e.getUpdatedAt(), e.getRemovedAt()
        );
    }
}
