package com.ecommerce.analytics.spark.streaming;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

/**
 * Pure DataFrame transformations for the streaming pipeline. Every method
 * here works identically whether it is given a real streaming Dataset (from
 * Kafka) or an ordinary static/batch Dataset built from a few in-memory
 * rows - Spark's DataFrame API is unified across both. That is what makes
 * BatchWindowingTest possible: it verifies the window-bucketing and
 * aggregation math against static DataFrames, with no Kafka, no socket, and
 * no real streaming query needed. (What it can NOT verify this way is
 * incremental micro-batch state or watermark-based row dropping/eviction -
 * those only happen in a genuine streaming execution, which is covered by
 * the end-to-end run described in README.md instead.)
 */
public final class StreamingAnalytics {

    private StreamingAnalytics() {}

    /**
     * Parses the raw Kafka "value" column (a JSON string - see EventEnvelope /
     * JacksonJsonSerializer on the producer side) into the envelope-level
     * columns every downstream aggregation needs, plus a handful of
     * event-type-specific fields pulled out of the polymorphic "payload"
     * object.
     *
     * get_json_object is used instead of a single from_json schema on
     * purpose: "payload" has a genuinely different shape per event type
     * (OrderCreatedEvent has nested "items", PaymentCreatedEvent doesn't,
     * etc.), so there is no one struct type that correctly describes every
     * message on this topic. get_json_object reads straight past that
     * without needing 20+ per-event-type schemas.
     */
    public static Dataset<Row> parseEnvelope(Dataset<Row> kafkaRaw) {
        Column value = col("value").cast("string");

        return kafkaRaw
                .withColumn("event_id", get_json_object(value, "$.eventId"))
                .withColumn("event_type", get_json_object(value, "$.eventType"))
                // occurredAt is ALWAYS a UTC instant (EventEnvelope#occurredAt is
                // java.time.Instant, always serialized with a trailing 'Z' - see
                // JacksonJsonSerializer on the producer side). Rather than relying on
                // Spark's to_timestamp() to correctly honor that 'Z'/offset marker
                // together with spark.sql.session.timeZone (Spark's handling of
                // explicit-offset strings combined with session-timezone conversion
                // has proven inconsistent in practice), the 'Z' is stripped here and
                // the remaining naive "yyyy-MM-ddTHH:mm:ss[.ffffff]" string is parsed
                // directly. With spark.sql.session.timeZone pinned to UTC (see both
                // Spark*Job main() methods and SparkTestSupport), parsing a naive
                // string is unambiguous and always correct: "wall clock in the
                // session timezone" IS "UTC" IS exactly what the 'Z' meant.
                .withColumn("event_time", to_timestamp(regexp_replace(get_json_object(value, "$.occurredAt"), "Z$", "")))
                .withColumn("aggregate_type", get_json_object(value, "$.aggregateType"))
                .withColumn("aggregate_id", get_json_object(value, "$.aggregateId").cast("long"))
                // ORDER_CREATED-specific
                .withColumn("order_total_amount", get_json_object(value, "$.payload.totalAmount").cast("double"))
                // PAYMENT_CREATED-specific
                .withColumn("payment_status", get_json_object(value, "$.payload.status"))
                .withColumn("payment_amount", get_json_object(value, "$.payload.amount").cast("double"))
                .filter(col("event_id").isNotNull().and(col("event_time").isNotNull()));
    }

    /** How many of each event type arrived per window - a general activity/health view. */
    public static Dataset<Row> eventCountsByWindow(Dataset<Row> parsed, String windowDuration, String watermarkDelay) {
        return parsed
                .withWatermark("event_time", watermarkDelay)
                .groupBy(window(col("event_time"), windowDuration), col("event_type"))
                .agg(count(lit(1)).alias("event_count"))
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("event_type"),
                        col("event_count")
                );
    }

    /** New orders and their gross value per window, from ORDER_CREATED events only. */
    public static Dataset<Row> orderActivityByWindow(Dataset<Row> parsed, String windowDuration, String watermarkDelay) {
        return parsed
                .filter(col("event_type").equalTo("ORDER_CREATED"))
                .withWatermark("event_time", watermarkDelay)
                .groupBy(window(col("event_time"), windowDuration))
                .agg(
                        count(lit(1)).alias("orders_created"),
                        sum(col("order_total_amount")).alias("gross_amount")
                )
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("orders_created"),
                        coalesce(col("gross_amount"), lit(0.0)).cast("decimal(14,2)").alias("gross_amount")
                );
    }

    /** Realized (PAID) revenue per window, from PAYMENT_CREATED events only - the near-real-time revenue counter. */
    public static Dataset<Row> paidRevenueByWindow(Dataset<Row> parsed, String windowDuration, String watermarkDelay) {
        return parsed
                .filter(col("event_type").equalTo("PAYMENT_CREATED").and(col("payment_status").equalTo("PAID")))
                .withWatermark("event_time", watermarkDelay)
                .groupBy(window(col("event_time"), windowDuration))
                .agg(
                        count(lit(1)).alias("paid_count"),
                        sum(col("payment_amount")).alias("paid_revenue")
                )
                .select(
                        col("window.start").alias("window_start"),
                        col("window.end").alias("window_end"),
                        col("paid_count"),
                        coalesce(col("paid_revenue"), lit(0.0)).cast("decimal(14,2)").alias("paid_revenue")
                );
    }
}
