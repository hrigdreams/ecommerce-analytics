package com.ecommerce.analytics.spark.batch;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.sql.Timestamp;

import static org.apache.spark.sql.functions.*;

/**
 * Pure DataFrame transformations - no SparkSession creation, no JDBC I/O -
 * so each one is unit-testable against small in-memory DataFrames (see
 * BatchAnalyticsTest) without a real Postgres database. SparkBatchAnalyticsJob
 * is the only class that reads/writes anything; this class only computes.
 *
 * Inputs are expected to already be filtered to the historical window
 * (created_at < cutoff) by the caller - see SparkBatchAnalyticsJob.
 *
 * Every join uses explicit DataFrame aliases (.as("o"), .as("p"), ...) and
 * plain "alias.column" string references rather than capturing .col()
 * handles across transformed/aggregated DataFrames - the latter is a
 * well-known Spark footgun (ambiguous/stale column references once a
 * DataFrame has been grouped or re-derived), so this style is used
 * throughout on purpose.
 */
public final class BatchAnalytics {

    private BatchAnalytics() {}

    /** Units sold, revenue and order count per product, counting only PAID orders. */
    public static Dataset<Row> productPerformance(
            Dataset<Row> orders, Dataset<Row> orderItems, Dataset<Row> products) {

        Dataset<Row> paid = orders.filter(col("payment_status").equalTo("PAID")).as("o");
        Dataset<Row> oi = orderItems.as("oi");

        Dataset<Row> soldItems = oi.join(paid, col("oi.order_id").equalTo(col("o.id")), "inner")
                .groupBy(col("oi.product_id").alias("product_id"))
                .agg(
                        sum(col("oi.quantity")).alias("units_sold"),
                        sum(col("oi.quantity").multiply(col("oi.unit_price"))).alias("revenue"),
                        countDistinct(col("oi.order_id")).alias("order_count")
                )
                .as("sold");

        return products.as("p")
                .join(soldItems, col("p.id").equalTo(col("sold.product_id")), "left")
                .select(
                        col("p.id").alias("product_id"),
                        col("p.name").alias("product_name"),
                        col("p.category_id").alias("category_id"),
                        coalesce(col("sold.units_sold"), lit(0)).alias("units_sold"),
                        coalesce(col("sold.revenue"), lit(0.0)).cast("decimal(14,2)").alias("revenue"),
                        coalesce(col("sold.order_count"), lit(0)).alias("order_count")
                );
    }

    /** Rolls product-level performance up to category level, plus how many distinct products each category has. */
    public static Dataset<Row> categoryPerformance(Dataset<Row> productPerformance, Dataset<Row> categories) {
        Dataset<Row> byCategory = productPerformance.as("pp")
                .groupBy(col("pp.category_id").alias("category_id"))
                .agg(
                        countDistinct(col("pp.product_id")).alias("product_count"),
                        sum(col("pp.units_sold")).alias("units_sold"),
                        sum(col("pp.revenue")).alias("revenue")
                )
                .as("bc");

        return categories.as("c")
                .join(byCategory, col("c.id").equalTo(col("bc.category_id")), "left")
                .select(
                        col("c.id").alias("category_id"),
                        col("c.name").alias("category_name"),
                        coalesce(col("bc.product_count"), lit(0)).alias("product_count"),
                        coalesce(col("bc.units_sold"), lit(0)).alias("units_sold"),
                        coalesce(col("bc.revenue"), lit(0.0)).cast("decimal(14,2)").alias("revenue")
                );
    }

    /** Per-customer order counts and spend - the "who are our best customers" view. */
    public static Dataset<Row> customerBehavior(Dataset<Row> orders) {
        return orders.as("o")
                .groupBy(col("o.user_id").alias("user_id"))
                .agg(
                        count(lit(1)).alias("total_orders"),
                        count(when(col("o.payment_status").equalTo("PAID"), 1)).alias("paid_orders"),
                        sum(when(col("o.payment_status").equalTo("PAID"), col("o.total_amount")).otherwise(0.0))
                                .alias("total_spend"),
                        min(col("o.created_at")).alias("first_order_at"),
                        max(col("o.created_at")).alias("last_order_at")
                )
                .withColumn("avg_order_value",
                        when(col("paid_orders").gt(0), col("total_spend").divide(col("paid_orders")))
                                .otherwise(lit(0.0)).cast("decimal(12,2)"))
                .withColumn("total_spend", col("total_spend").cast("decimal(14,2)"));
    }

    /** Daily order/revenue trend across the whole historical window. */
    public static Dataset<Row> dailyRevenueTrend(Dataset<Row> orders) {
        return orders
                .withColumn("day", to_date(col("created_at")))
                .groupBy(col("day"))
                .agg(
                        count(lit(1)).alias("order_count"),
                        count(when(col("payment_status").equalTo("PAID"), 1)).alias("paid_order_count"),
                        sum(when(col("payment_status").equalTo("PAID"), col("total_amount")).otherwise(0.0))
                                .cast("decimal(14,2)").alias("revenue")
                )
                .orderBy(col("day"));
    }

    /** Orders/revenue by customer country+city - where the demand actually is. */
    public static Dataset<Row> geoPerformance(Dataset<Row> orders, Dataset<Row> users) {
        return orders.as("o")
                .join(users.as("u"), col("o.user_id").equalTo(col("u.id")), "inner")
                .groupBy(col("u.country").alias("country"), col("u.city").alias("city"))
                .agg(
                        count(lit(1)).alias("order_count"),
                        count(when(col("o.payment_status").equalTo("PAID"), 1)).alias("paid_order_count"),
                        sum(when(col("o.payment_status").equalTo("PAID"), col("o.total_amount")).otherwise(0.0))
                                .cast("decimal(14,2)").alias("revenue")
                )
                .orderBy(col("revenue").desc());
    }

    /**
     * Recurring hour-of-day demand pattern (0-23), aggregated across the WHOLE
     * historical window. Deliberately different from the Spring app's
     * TimeAnalytics, which buckets by absolute HOUR/DAY/WEEK/MONTH/YEAR
     * timestamps (a timeline); this answers "what time of day do people
     * shop", which a full historical scan like Spark's can answer cheaply -
     * the transactional service was never meant to compute it.
     */
    public static Dataset<Row> hourOfDayPattern(Dataset<Row> orders) {
        return orders
                .withColumn("hour_of_day", hour(col("created_at")))
                .groupBy(col("hour_of_day"))
                .agg(
                        count(lit(1)).alias("order_count"),
                        sum(when(col("payment_status").equalTo("PAID"), col("total_amount")).otherwise(0.0))
                                .cast("decimal(14,2)").alias("revenue")
                )
                .orderBy(col("hour_of_day"));
    }

    /**
     * Time-decayed popularity score per product: every PAID order item
     * contributes {@code quantity * exp(-ln(2)/halfLifeDays * ageInDays)} to
     * its product's score, so a unit sold today counts for ~1.0, a unit sold
     * one half-life ago counts for ~0.5, two half-lives ago ~0.25, and so
     * on - recent sales dominate, old sales fade out smoothly rather than
     * dropping off a hard cliff at some fixed "last N days" boundary.
     * Exponential decay (rather than e.g. a flat recent-window cutoff) is
     * the standard choice for this kind of "recency-weighted popularity"
     * signal precisely because it has no arbitrary edge - nothing jumps or
     * disappears the instant it crosses a day-count threshold.
     * <p>
     * Deliberately only counts PAID orders (same filter productPerformance
     * already uses) - an unpaid/pending/cancelled order was never a real
     * sale and should not inflate a "how popular is this right now" signal,
     * any more than it counts toward units_sold/revenue elsewhere in this
     * class.
     * <p>
     * {@code asOf} is passed in (not computed as "now" inside this method)
     * so the computation stays a pure, deterministic function of its inputs
     * for testability - see BatchAnalyticsTest's existing style, and
     * ProductPopularityAnalyticsTest for this method's own tests.
     *
     * @param orders       full orders DataFrame (payment_status, created_at, ...)
     * @param orderItems   full order_items DataFrame (order_id, product_id, quantity, ...)
     * @param products     products DataFrame - left-joined against so EVERY product
     *                     appears in the output (score 0.0 if never ordered), matching
     *                     productPerformance's "every product appears" convention
     * @param asOf         the reference instant "now" is measured from
     * @param halfLifeDays after this many days, a unit of past quantity contributes half
     *                     as much as it did on the day of sale; must be &gt; 0
     */
    public static Dataset<Row> productPopularity(
            Dataset<Row> orders, Dataset<Row> orderItems, Dataset<Row> products,
            Timestamp asOf, double halfLifeDays) {

        if (halfLifeDays <= 0) {
            throw new IllegalArgumentException("halfLifeDays must be > 0, got " + halfLifeDays);
        }

        double lambda = Math.log(2.0) / halfLifeDays;

        Dataset<Row> paid = orders.filter(col("payment_status").equalTo("PAID")).as("o");
        Dataset<Row> oi = orderItems.as("oi");

        // age in days = (asOf - created_at) in seconds / 86400, clamped at 0 so a
        // clock-skewed/future-dated row can never produce a > 1.0 "bonus" weight.
        Dataset<Row> weighted = oi.join(paid, col("oi.order_id").equalTo(col("o.id")), "inner")
                .withColumn("age_days",
                        greatest(
                                (lit(asOf.getTime() / 1000.0).minus(unix_timestamp(col("o.created_at")))).divide(86400.0),
                                lit(0.0)))
                .withColumn("decay_weight", exp(lit(-lambda).multiply(col("age_days"))))
                .withColumn("weighted_quantity", col("oi.quantity").multiply(col("decay_weight")))
                .groupBy(col("oi.product_id").alias("product_id"))
                .agg(sum(col("weighted_quantity")).alias("popularity_score"))
                .as("w");

        return products.as("p")
                .join(weighted, col("p.id").equalTo(col("w.product_id")), "left")
                .select(
                        col("p.id").alias("product_id"),
                        coalesce(col("w.popularity_score"), lit(0.0)).cast("decimal(14,6)").alias("popularity_score"),
                        lit(asOf).alias("computed_at")
                );
    }
}
