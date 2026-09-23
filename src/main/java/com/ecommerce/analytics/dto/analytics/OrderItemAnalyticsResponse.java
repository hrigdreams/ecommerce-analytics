package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.OrderItemAnalytics;

import java.math.BigDecimal;

public record OrderItemAnalyticsResponse(
        Long orderId,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static OrderItemAnalyticsResponse from(OrderItemAnalytics e) {
        return new OrderItemAnalyticsResponse(
                e.getOrderId(), e.getProductId(), e.getProductName(),
                e.getQuantity(), e.getUnitPrice(), e.getLineTotal()
        );
    }
}
