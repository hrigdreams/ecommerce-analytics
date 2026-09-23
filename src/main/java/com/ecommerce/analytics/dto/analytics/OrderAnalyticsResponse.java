package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.OrderAnalytics;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderAnalyticsResponse(
        Long orderId,
        Long userId,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        boolean paid,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderAnalyticsResponse from(OrderAnalytics e) {
        return new OrderAnalyticsResponse(
                e.getOrderId(), e.getUserId(), e.getStatus(), e.getPaymentStatus(),
                e.getTotalAmount(), e.isPaid(), e.getPaidAt(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
