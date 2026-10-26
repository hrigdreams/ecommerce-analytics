package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.OrderAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface OrderAnalyticsRepository extends JpaRepository<OrderAnalytics, Long> {

    /**
     * PHASE 1, item 5 (average order value): the existing GET /analytics/time
     * endpoint already returns per-bucket orders/paidOrders/revenue (its
     * TimeAnalyticsResponse), from which AOV = revenue / paidOrders can be
     * derived -- but that DTO does not expose an averageOrderValue field
     * directly, so a client has to do that division itself per bucket. These
     * two queries back a new, dedicated GET /analytics/order-value endpoint
     * (AdvancedAnalyticsController) that returns one already-computed global
     * AOV figure over an arbitrary [from, to) range, paid orders only --
     * consistent with how "revenue" is defined everywhere else in this
     * codebase (product_analytics, category_analytics, time_analytics all
     * filter to payment_status = 'PAID' / paid = true for their revenue
     * sums).
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM OrderAnalytics o
            WHERE o.paid = true AND o.createdAt >= :from AND o.createdAt < :to
            """)
    BigDecimal sumPaidRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT COUNT(o)
            FROM OrderAnalytics o
            WHERE o.paid = true AND o.createdAt >= :from AND o.createdAt < :to
            """)
    long countPaidBetween(@Param("from") Instant from, @Param("to") Instant to);
}
