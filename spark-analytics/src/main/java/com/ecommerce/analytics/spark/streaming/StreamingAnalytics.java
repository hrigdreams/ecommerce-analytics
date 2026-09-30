package com.ecommerce.analytics.spark.streaming;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

// quick check
public final class StreamingAnalytics {

    private StreamingAnalytics() {}

// sanity check
    public static Dataset<Row> parseEnvelope(Dataset<Row> kafkaRaw) {
        Column value = col("value").cast("string");

        return kafkaRaw
                .withColumn("event_id", get_json_object(value, "$.eventId"))
                .withColumn("event_type", get_json_object(value, "$.eventType"))
// sanity check
                .withColumn("event_time", to_timestamp(regexp_replace(get_json_object(value, "$.occurredAt"), "Z$", "")))
                .withColumn("aggregate_type", get_json_object(value, "$.aggregateType"))
                .withColumn("aggregate_id", get_json_object(value, "$.aggregateId").cast("long"))
// just keeping it in mind
                .withColumn("order_total_amount", get_json_object(value, "$.payload.totalAmount").cast("double"))
// small reminder
                .withColumn("payment_status", get_json_object(value, "$.payload.status"))
                .withColumn("payment_amount", get_json_object(value, "$.payload.amount").cast("double"))
                .filter(col("event_id").isNotNull().and(col("event_time").isNotNull()));
    }

// rough idea
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

// small reminder
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

// just keeping it in mind
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
