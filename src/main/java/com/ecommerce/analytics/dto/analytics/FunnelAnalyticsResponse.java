package com.ecommerce.analytics.dto.analytics;

import com.ecommerce.analytics.entity.FunnelAnalytics;

import java.time.Instant;

/**
 * PHASE 1, item 5 (conversion funnel metrics). Wraps one day's
 * FunnelAnalytics row (already correctly populated by FunnelAnalyticsService
 * - see that class) plus stage-to-stage conversion rates computed here, so
 * the frontend doesn't have to re-derive "cartAdded / views" itself from raw
 * counts on every render. Rates are null (not 0, not NaN/Infinity) when the
 * previous stage's count is 0, since "0% conversion from 0 views" is a
 * different, misleading claim from "no data to compute a rate from".
 */
public record FunnelAnalyticsResponse(
        Instant bucketDate,
        long productViews,
        long cartItemAdded,
        long checkoutStarted,
        long ordersCreated,
        long ordersPaid,
        Double viewToCartRate,
        Double cartToCheckoutRate,
        Double checkoutToOrderRate,
        Double orderToPaidRate,
        Instant updatedAt
) {
    public static FunnelAnalyticsResponse from(FunnelAnalytics e) {
        return new FunnelAnalyticsResponse(
                e.getBucketDate(),
                e.getProductViews(),
                e.getCartItemAdded(),
                e.getCheckoutStarted(),
                e.getOrdersCreated(),
                e.getOrdersPaid(),
                rate(e.getCartItemAdded(), e.getProductViews()),
                rate(e.getCheckoutStarted(), e.getCartItemAdded()),
                rate(e.getOrdersCreated(), e.getCheckoutStarted()),
                rate(e.getOrdersPaid(), e.getOrdersCreated()),
                e.getUpdatedAt()
        );
    }

    private static Double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round((numerator * 10000.0) / denominator) / 10000.0;
    }
}
