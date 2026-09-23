package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.CartAnalytics;

import java.time.Instant;

public record CartAnalyticsResponse(
        Long cartId,
        Long userId,
        int itemCount,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Instant lastActivityAt,
        Instant deletedAt
) {
    public static CartAnalyticsResponse from(CartAnalytics e) {
        return new CartAnalyticsResponse(
                e.getCartId(), e.getUserId(), e.getItemCount(), e.isActive(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getLastActivityAt(), e.getDeletedAt()
        );
    }
}
