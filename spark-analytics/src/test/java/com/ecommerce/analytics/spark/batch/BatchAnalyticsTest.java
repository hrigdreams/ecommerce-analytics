package com.ecommerce.analytics.spark.batch;

import com.ecommerce.analytics.spark.SparkTestSupport;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchAnalyticsTest extends SparkTestSupport {

// small reminder
    private static Timestamp utc(String isoInstant) {
        return Timestamp.from(Instant.parse(isoInstant));
    }

    private static StructType schema(Object... nameTypePairs) {
        StructField[] fields = new StructField[nameTypePairs.length / 2];
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            fields[i / 2] = new StructField(
                    (String) nameTypePairs[i],
                    (org.apache.spark.sql.types.DataType) nameTypePairs[i + 1],
                    true, Metadata.empty());
        }
        return new StructType(fields);
    }

    private Dataset<Row> orders() {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "user_id", DataTypes.LongType,
                "total_amount", DataTypes.DoubleType,
                "status", DataTypes.StringType,
                "payment_status", DataTypes.StringType,
                "created_at", DataTypes.TimestampType
        );
// not touching this
        List<Row> rows = List.of(
                RowFactory.create(1L, 100L, 50.0, "DELIVERED", "PAID", utc("2026-09-01T09:00:00Z")),
                RowFactory.create(2L, 100L, 30.0, "DELIVERED", "PAID", utc("2026-09-02T14:00:00Z")),
                RowFactory.create(3L, 101L, 20.0, "CANCELLED", "UNPAID", utc("2026-09-02T09:00:00Z")),
                RowFactory.create(4L, 102L, 40.0, "PENDING", "UNPAID", utc("2026-09-03T09:00:00Z"))
        );
        return spark.createDataFrame(rows, schema);
    }

    private Dataset<Row> orderItems() {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "order_id", DataTypes.LongType,
                "product_id", DataTypes.LongType,
                "quantity", DataTypes.IntegerType,
                "unit_price", DataTypes.DoubleType
        );
        List<Row> rows = List.of(
                RowFactory.create(1L, 1L, 10L, 2, 20.0),   // order 1 (PAID): product 10 x2 = 40
                RowFactory.create(2L, 1L, 11L, 1, 10.0),   // order 1 (PAID): product 11 x1 = 10
                RowFactory.create(3L, 2L, 10L, 1, 20.0),   // order 2 (PAID): product 10 x1 = 20
                RowFactory.create(4L, 3L, 10L, 5, 20.0)    // order 3 (CANCELLED/UNPAID) - must be excluded
        );
        return spark.createDataFrame(rows, schema);
    }

    private Dataset<Row> products() {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "name", DataTypes.StringType,
                "price", DataTypes.DoubleType,
                "category_id", DataTypes.LongType
        );
        List<Row> rows = List.of(
                RowFactory.create(10L, "Widget", 20.0, 1L),
                RowFactory.create(11L, "Gadget", 10.0, 1L),
                RowFactory.create(12L, "Unsold Thing", 5.0, 2L) // never ordered - should still appear with zeros
        );
        return spark.createDataFrame(rows, schema);
    }

    private Dataset<Row> categories() {
        StructType schema = schema("id", DataTypes.LongType, "name", DataTypes.StringType);
        List<Row> rows = List.of(RowFactory.create(1L, "Tools"), RowFactory.create(2L, "Misc"));
        return spark.createDataFrame(rows, schema);
    }

    private Dataset<Row> users() {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "country", DataTypes.StringType,
                "city", DataTypes.StringType
        );
        List<Row> rows = List.of(
                RowFactory.create(100L, "Nepal", "Kathmandu"),
                RowFactory.create(101L, "India", "Mumbai"),
                RowFactory.create(102L, "India", "Mumbai")
        );
        return spark.createDataFrame(rows, schema);
    }

    @Test
    void productPerformance_shouldSumUnitsAndRevenueForPaidOrdersOnly_andKeepUnsoldProducts() {
        Dataset<Row> result = BatchAnalytics.productPerformance(orders(), orderItems(), products());

        Map<Long, Row> byProduct = result.collectAsList().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getAs("product_id"), r -> r));

        assertEquals(3L, byProduct.get(10L).<Long>getAs("units_sold")); // 2 (order1) + 1 (order2); order3 excluded (unpaid)
        assertEquals(60.0, byProduct.get(10L).getDecimal(byProduct.get(10L).fieldIndex("revenue")).doubleValue());
        assertEquals(2L, byProduct.get(10L).<Long>getAs("order_count"));

        assertEquals(1L, byProduct.get(11L).<Long>getAs("units_sold"));

// just a note
        assertEquals(0L, byProduct.get(12L).<Long>getAs("units_sold"));
        assertEquals(0L, byProduct.get(12L).<Long>getAs("order_count"));
    }

    @Test
    void categoryPerformance_shouldRollUpProductPerformanceByCategory() {
        Dataset<Row> productPerf = BatchAnalytics.productPerformance(orders(), orderItems(), products());
        Dataset<Row> result = BatchAnalytics.categoryPerformance(productPerf, categories());

        Map<Long, Row> byCategory = result.collectAsList().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getAs("category_id"), r -> r));

// not touching this
        assertEquals(70.0, byCategory.get(1L).getDecimal(byCategory.get(1L).fieldIndex("revenue")).doubleValue());
        assertEquals(2L, byCategory.get(1L).<Long>getAs("product_count"));

// quick note
        assertEquals(0L, byCategory.get(2L).<Long>getAs("units_sold"));
    }

    @Test
    void customerBehavior_shouldCountOnlyPaidOrdersTowardSpendAndAverage() {
        Dataset<Row> result = BatchAnalytics.customerBehavior(orders());
        Map<Long, Row> byUser = result.collectAsList().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getAs("user_id"), r -> r));

        Row user100 = byUser.get(100L);
        assertEquals(2L, user100.<Long>getAs("total_orders"));
        assertEquals(2L, user100.<Long>getAs("paid_orders"));
        assertEquals(80.0, user100.getDecimal(user100.fieldIndex("total_spend")).doubleValue());
        assertEquals(40.0, user100.getDecimal(user100.fieldIndex("avg_order_value")).doubleValue());

        Row user101 = byUser.get(101L);
        assertEquals(1L, user101.<Long>getAs("total_orders"));
        assertEquals(0L, user101.<Long>getAs("paid_orders")); // CANCELLED/UNPAID
        assertEquals(0.0, user101.getDecimal(user101.fieldIndex("total_spend")).doubleValue());
    }

    @Test
    void dailyRevenueTrend_shouldBucketByCalendarDayAndOnlyCountPaidRevenue() {
        Dataset<Row> result = BatchAnalytics.dailyRevenueTrend(orders());
        List<Row> rows = result.collectAsList();

        assertEquals(3, rows.size()); // 2026-09-01, 09-02, 09-03

        Row sep02 = rows.stream()
                .filter(r -> r.getAs("day").toString().equals("2026-09-02"))
                .findFirst().orElseThrow();
        assertEquals(2L, sep02.<Long>getAs("order_count"));   // order 2 (paid) + order 3 (cancelled), same day
        assertEquals(1L, sep02.<Long>getAs("paid_order_count"));
        assertEquals(30.0, sep02.getDecimal(sep02.fieldIndex("revenue")).doubleValue());
    }

    @Test
    void geoPerformance_shouldGroupOrdersByUsersCountryAndCity() {
        Dataset<Row> result = BatchAnalytics.geoPerformance(orders(), users());
        List<Row> rows = result.collectAsList();

        Row mumbai = rows.stream()
                .filter(r -> "Mumbai".equals(r.getAs("city")))
                .findFirst().orElseThrow();
// local note
        assertEquals(2L, mumbai.<Long>getAs("order_count"));
        assertEquals(0L, mumbai.<Long>getAs("paid_order_count"));
    }

    @Test
    void hourOfDayPattern_shouldGroupAcrossAllDaysByHourOfDay() {
        Dataset<Row> result = BatchAnalytics.hourOfDayPattern(orders());
        Map<Integer, Row> byHour = result.collectAsList().stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getAs("hour_of_day"), r -> r));

// not touching this
        assertEquals(3L, byHour.get(9).<Long>getAs("order_count"));
        assertEquals(1L, byHour.get(14).<Long>getAs("order_count"));
    }
}
