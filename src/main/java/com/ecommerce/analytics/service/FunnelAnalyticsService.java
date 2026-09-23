package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.FunnelAnalytics;
import com.ecommerce.analytics.repository.FunnelAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

/**
 * Daily funnel rollup. Each stage is recorded independently as its event
 * arrives (they're on different Kafka events / different aggregates), so this
 * does not try to correlate individual users across stages — it counts stage
 * occurrences per day, which is enough for conversion-rate style funnel charts.
 */
@Service
public class FunnelAnalyticsService {

    private final FunnelAnalyticsRepository repository;

    public FunnelAnalyticsService(FunnelAnalyticsRepository repository) {
        this.repository = repository;
    }

    private Instant dayBucket(Instant at) {
        return at.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
    }

    private void increment(Instant occurredAt, Consumer<FunnelAnalytics> mutate) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        Instant bucket = dayBucket(at);

        FunnelAnalytics row = repository.findByBucketDate(bucket).orElseGet(() -> {
            FunnelAnalytics created = new FunnelAnalytics();
            created.setBucketDate(bucket);
            return created;
        });

        mutate.accept(row);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    /** PRODUCT_VIEWED */
    @Transactional
    public void recordProductViewed(Instant occurredAt) {
        increment(occurredAt, row -> row.setProductViews(row.getProductViews() + 1));
    }

    /** CART_ITEM_ADDED */
    @Transactional
    public void recordCartItemAdded(Instant occurredAt) {
        increment(occurredAt, row -> row.setCartItemAdded(row.getCartItemAdded() + 1));
    }

    /** CHECKOUT_STARTED */
    @Transactional
    public void recordCheckoutStarted(Instant occurredAt) {
        increment(occurredAt, row -> row.setCheckoutStarted(row.getCheckoutStarted() + 1));
    }

    /** ORDER_CREATED */
    @Transactional
    public void recordOrderCreated(Instant occurredAt) {
        increment(occurredAt, row -> row.setOrdersCreated(row.getOrdersCreated() + 1));
    }

    /** First PAYMENT_CREATED that makes an order PAID */
    @Transactional
    public void recordOrderPaid(Instant occurredAt) {
        increment(occurredAt, row -> row.setOrdersPaid(row.getOrdersPaid() + 1));
    }
}
