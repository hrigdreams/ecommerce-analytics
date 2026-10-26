package com.ecommerce.analytics.dto.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * PHASE 1, item 5 (average order value). A single, already-computed AOV
 * figure for an arbitrary [from, to) range, paid orders only. Backs
 * GET /api/v1/analytics/order-value. This is additive to, not a replacement
 * for, the existing GET /analytics/time endpoint, which already returns
 * orders/paidOrders/revenue per bucket (HOUR/DAY/WEEK/MONTH/YEAR) -- that
 * endpoint is still the right place for a trend of AOV over time. This one
 * answers the simpler, more common question ("what's our average order
 * value right now") in one field instead of requiring the client to fetch
 * every bucket and divide revenue by paidOrders itself.
 */
public record OrderValueSummaryResponse(
        Instant from,
        Instant to,
        long paidOrderCount,
        BigDecimal totalRevenue,
        BigDecimal averageOrderValue
) {
    public static OrderValueSummaryResponse of(Instant from, Instant to, long paidOrderCount, BigDecimal totalRevenue) {
        BigDecimal aov = paidOrderCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(paidOrderCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new OrderValueSummaryResponse(from, to, paidOrderCount, totalRevenue, aov);
    }
}
