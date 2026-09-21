package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.TimeAnalytics;
import com.ecommerce.analytics.repository.TimeAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Maintains hour/day/week/month/year rollups. All bucketing is done in UTC so
 * results are deterministic regardless of server timezone.
 */
@Service
public class TimeAnalyticsService {

    private static final List<String> BUCKET_TYPES =
            List.of(TimeAnalytics.HOUR, TimeAnalytics.DAY, TimeAnalytics.WEEK, TimeAnalytics.MONTH, TimeAnalytics.YEAR);

    private final TimeAnalyticsRepository repository;

    public TimeAnalyticsService(TimeAnalyticsRepository repository) {
        this.repository = repository;
    }

    private Instant bucketStart(String bucketType, Instant at) {
        ZonedDateTime utc = at.atZone(ZoneOffset.UTC);
        return switch (bucketType) {
            case TimeAnalytics.HOUR -> utc.truncatedTo(ChronoUnit.HOURS).toInstant();
            case TimeAnalytics.DAY -> utc.truncatedTo(ChronoUnit.DAYS).toInstant();
            case TimeAnalytics.WEEK -> utc.truncatedTo(ChronoUnit.DAYS)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .toInstant();
            case TimeAnalytics.MONTH -> utc.truncatedTo(ChronoUnit.DAYS)
                    .withDayOfMonth(1)
                    .toInstant();
            case TimeAnalytics.YEAR -> utc.truncatedTo(ChronoUnit.DAYS)
                    .withDayOfYear(1)
                    .toInstant();
            default -> throw new IllegalArgumentException("Unknown bucket type: " + bucketType);
        };
    }

    private TimeAnalytics getOrCreate(String bucketType, Instant bucketStart) {
        return repository.findByBucketTypeAndBucketStart(bucketType, bucketStart).orElseGet(() -> {
            TimeAnalytics row = new TimeAnalytics();
            row.setBucketType(bucketType);
            row.setBucketStart(bucketStart);
            return row;
        });
    }

    /** Called once per order, bucketed by the order's own created_at. */
    @Transactional
    public void recordOrderCreated(Instant orderCreatedAt) {
        Instant at = orderCreatedAt != null ? orderCreatedAt : Instant.now();
        for (String bucketType : BUCKET_TYPES) {
            TimeAnalytics row = getOrCreate(bucketType, bucketStart(bucketType, at));
            row.setOrders(row.getOrders() + 1);
            row.setUpdatedAt(Instant.now());
            repository.save(row);
        }
    }

    /**
     * Called once per order the first time it becomes PAID. Bucketed by the
     * order's created_at (not the payment time) so an order's revenue always
     * lands in the same bucket as the order itself.
     */
    @Transactional
    public void recordOrderPaid(Instant orderCreatedAt, BigDecimal revenue, long unitsSold) {
        Instant at = orderCreatedAt != null ? orderCreatedAt : Instant.now();
        for (String bucketType : BUCKET_TYPES) {
            TimeAnalytics row = getOrCreate(bucketType, bucketStart(bucketType, at));
            row.setPaidOrders(row.getPaidOrders() + 1);
            row.setRevenue(row.getRevenue().add(revenue != null ? revenue : BigDecimal.ZERO));
            row.setUnitsSold(row.getUnitsSold() + unitsSold);
            row.setUpdatedAt(Instant.now());
            repository.save(row);
        }
    }
}
