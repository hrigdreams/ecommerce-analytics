package com.ecommerce.analytics.spark.batch;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.*;

// rough idea
public final class BatchAnalytics {

    private BatchAnalytics() {
    }

// just keeping it in mind
    public static Dataset<Row> productPerformance(
            Dataset<Row> orders,
            Dataset<Row> orderItems,
            Dataset<Row> products) {

        Dataset<Row> paid = orders
                .filter(col("payment_status").equalTo("PAID"))
                .as("o");

        Dataset<Row> oi = orderItems.as("oi");

        Dataset<Row> soldItems = oi
                .join(
                        paid,
                        col("oi.order_id").equalTo(col("o.id")),
                        "inner"
                )
                .groupBy(col("oi.product_id").alias("product_id"))
                .agg(
                        sum(col("oi.quantity")).alias("units_sold"),
                        sum(
                                col("oi.quantity")
                                        .multiply(col("oi.unit_price"))
                        ).alias("revenue"),
                        countDistinct(col("oi.order_id"))
                                .alias("order_count")
                )
                .as("sold");

        return products
                .as("p")
                .join(
                        soldItems,
                        col("p.id").equalTo(col("sold.product_id")),
                        "left"
                )
                .select(
                        col("p.id").alias("product_id"),
                        col("p.name").alias("product_name"),
                        col("p.category_id").alias("category_id"),
                        coalesce(
                                col("sold.units_sold"),
                                lit(0)
                        ).alias("units_sold"),
                        coalesce(
                                col("sold.revenue"),
                                lit(0.0)
                        )
                                .cast("decimal(14,2)")
                                .alias("revenue"),
                        coalesce(
                                col("sold.order_count"),
                                lit(0)
                        ).alias("order_count")
                );
    }

// small reminder
    public static Dataset<Row> categoryPerformance(
            Dataset<Row> productPerformance,
            Dataset<Row> categories) {

        Dataset<Row> byCategory = productPerformance
                .as("pp")
                .groupBy(col("pp.category_id").alias("category_id"))
                .agg(
                        countDistinct(col("pp.product_id"))
                                .alias("product_count"),
                        sum(col("pp.units_sold"))
                                .alias("units_sold"),
                        sum(col("pp.revenue"))
                                .alias("revenue")
                )
                .as("bc");

        return categories
                .as("c")
                .join(
                        byCategory,
                        col("c.id").equalTo(col("bc.category_id")),
                        "left"
                )
                .select(
                        col("c.id").alias("category_id"),
                        col("c.name").alias("category_name"),
                        coalesce(
                                col("bc.product_count"),
                                lit(0)
                        ).alias("product_count"),
                        coalesce(
                                col("bc.units_sold"),
                                lit(0)
                        ).alias("units_sold"),
                        coalesce(
                                col("bc.revenue"),
                                lit(0.0)
                        )
                                .cast("decimal(14,2)")
                                .alias("revenue")
                );
    }

// quick check
    public static Dataset<Row> customerBehavior(Dataset<Row> orders) {

        return orders
                .as("o")
                .groupBy(col("o.user_id").alias("user_id"))
                .agg(
                        count(lit(1)).alias("total_orders"),

                        count(
                                when(
                                        col("o.payment_status")
                                                .equalTo("PAID"),
                                        1
                                )
                        ).alias("paid_orders"),

                        sum(
                                when(
                                        col("o.payment_status")
                                                .equalTo("PAID"),
                                        col("o.total_amount")
                                ).otherwise(0.0)
                        ).alias("total_spend"),

                        min(col("o.created_at"))
                                .alias("first_order_at"),

                        max(col("o.created_at"))
                                .alias("last_order_at")
                )
                .withColumn(
                        "avg_order_value",
                        when(
                                col("paid_orders").gt(0),
                                col("total_spend")
                                        .divide(col("paid_orders"))
                        )
                                .otherwise(lit(0.0))
                                .cast("decimal(12,2)")
                )
                .withColumn(
                        "total_spend",
                        col("total_spend")
                                .cast("decimal(14,2)")
                );
    }

// quick check
    public static Dataset<Row> dailyRevenueTrend(Dataset<Row> orders) {

        return orders
                .withColumn(
                        "day",
                        to_date(col("created_at"))
                )
                .groupBy(col("day"))
                .agg(
                        count(lit(1))
                                .alias("order_count"),

                        count(
                                when(
                                        col("payment_status")
                                                .equalTo("PAID"),
                                        1
                                )
                        ).alias("paid_order_count"),

                        sum(
                                when(
                                        col("payment_status")
                                                .equalTo("PAID"),
                                        col("total_amount")
                                ).otherwise(0.0)
                        )
                                .cast("decimal(14,2)")
                                .alias("revenue")
                )
                .orderBy(col("day"));
    }

// rough idea
    public static Dataset<Row> geoPerformance(
            Dataset<Row> orders,
            Dataset<Row> users) {

        return orders
                .as("o")
                .join(
                        users.as("u"),
                        col("o.user_id").equalTo(col("u.id")),
                        "inner"
                )
                .groupBy(
                        col("u.country").alias("country"),
                        col("u.city").alias("city")
                )
                .agg(
                        count(lit(1))
                                .alias("order_count"),

                        count(
                                when(
                                        col("o.payment_status")
                                                .equalTo("PAID"),
                                        1
                                )
                        ).alias("paid_order_count"),

                        sum(
                                when(
                                        col("o.payment_status")
                                                .equalTo("PAID"),
                                        col("o.total_amount")
                                ).otherwise(0.0)
                        )
                                .cast("decimal(14,2)")
                                .alias("revenue")
                )
                .orderBy(col("revenue").desc());
    }

// quick note
    public static Dataset<Row> hourOfDayPattern(Dataset<Row> orders) {

        return orders
                .filter(
                        col("payment_status")
                                .notEqual("CANCELLED")
                )
                .withColumn(
                        "hour_of_day",
                        hour(col("created_at"))
                )
                .groupBy(col("hour_of_day"))
                .agg(
                        count(lit(1))
                                .alias("order_count"),

                        sum(
                                when(
                                        col("payment_status")
                                                .equalTo("PAID"),
                                        col("total_amount")
                                ).otherwise(0.0)
                        )
                                .cast("decimal(14,2)")
                                .alias("revenue")
                )
                .orderBy(col("hour_of_day"));
    }
}