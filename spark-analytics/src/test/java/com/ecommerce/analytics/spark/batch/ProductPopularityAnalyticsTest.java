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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Separate test class (not appended to the existing BatchAnalyticsTest) to
 * avoid rewriting a large existing test file blind - same fixture style
 * (schema()/utc() helpers, RowFactory rows) as BatchAnalyticsTest, just
 * local to this class.
 */
class ProductPopularityAnalyticsTest extends SparkTestSupport {

    private static final double HALF_LIFE_DAYS = 10.0;
    private static final Timestamp NOW = utc("2026-09-20T00:00:00Z");

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

    private Dataset<Row> orders(Row... rows) {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "user_id", DataTypes.LongType,
                "total_amount", DataTypes.DoubleType,
                "status", DataTypes.StringType,
                "payment_status", DataTypes.StringType,
                "created_at", DataTypes.TimestampType
        );
        return spark.createDataFrame(List.of(rows), schema);
    }

    private Dataset<Row> orderItems(Row... rows) {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "order_id", DataTypes.LongType,
                "product_id", DataTypes.LongType,
                "quantity", DataTypes.IntegerType,
                "unit_price", DataTypes.DoubleType
        );
        return spark.createDataFrame(List.of(rows), schema);
    }

    private Dataset<Row> products(Row... rows) {
        StructType schema = schema(
                "id", DataTypes.LongType,
                "name", DataTypes.StringType,
                "price", DataTypes.DoubleType,
                "category_id", DataTypes.LongType
        );
        return spark.createDataFrame(List.of(rows), schema);
    }

    private double popularityOf(Dataset<Row> result, long productId) {
        Row row = result.filter("product_id = " + productId).first();
        return row.getDecimal(row.fieldIndex("popularity_score")).doubleValue();
    }

    @Test
    void productNeverOrdered_shouldStillAppearWithZeroScore() {
        Dataset<Row> orders = orders();
        Dataset<Row> items = orderItems();
        Dataset<Row> products = products(RowFactory.create(10L, "Widget", 20.0, 1L));

        Dataset<Row> result = BatchAnalytics.productPopularity(orders, items, products, NOW, HALF_LIFE_DAYS);

        assertEquals(1, result.count());
        assertEquals(0.0, popularityOf(result, 10L), 1e-9);
    }

    @Test
    void orderSoldToday_shouldContributeApproximatelyItsFullQuantity() {
        Dataset<Row> orders = orders(RowFactory.create(1L, 100L, 50.0, "DELIVERED", "PAID", NOW));
        Dataset<Row> items = orderItems(RowFactory.create(1L, 1L, 10L, 4, 20.0));
        Dataset<Row> products = products(RowFactory.create(10L, "Widget", 20.0, 1L));

        Dataset<Row> result = BatchAnalytics.productPopularity(orders, items, products, NOW, HALF_LIFE_DAYS);

        // age = 0 days -> decay weight ~= 1.0 -> score ~= quantity (4)
        assertEquals(4.0, popularityOf(result, 10L), 1e-6);
    }

    @Test
    void orderSoldOneHalfLifeAgo_shouldContributeApproximatelyHalfItsQuantity() {
        Timestamp oneHalfLifeAgo = Timestamp.from(NOW.toInstant().minusSeconds((long) (HALF_LIFE_DAYS * 86400)));
        Dataset<Row> orders = orders(RowFactory.create(1L, 100L, 50.0, "DELIVERED", "PAID", oneHalfLifeAgo));
        Dataset<Row> items = orderItems(RowFactory.create(1L, 1L, 10L, 10, 20.0));
        Dataset<Row> products = products(RowFactory.create(10L, "Widget", 20.0, 1L));

        Dataset<Row> result = BatchAnalytics.productPopularity(orders, items, products, NOW, HALF_LIFE_DAYS);

        // age = exactly 1 half-life -> decay weight = 0.5 -> score ~= 5.0
        assertEquals(5.0, popularityOf(result, 10L), 1e-3);
    }

    @Test
    void recentSaleShouldOutweighALargerButOlderSale() {
        Timestamp twoHalfLivesAgo = Timestamp.from(NOW.toInstant().minusSeconds((long) (2 * HALF_LIFE_DAYS * 86400)));
        // Product 10: 3 units sold TODAY. Product 20: 100 units sold two half-lives ago (weight ~0.25 -> ~25).
        Dataset<Row> orders = orders(
                RowFactory.create(1L, 100L, 50.0, "DELIVERED", "PAID", NOW),
                RowFactory.create(2L, 101L, 50.0, "DELIVERED", "PAID", twoHalfLivesAgo)
        );
        Dataset<Row> items = orderItems(
                RowFactory.create(1L, 1L, 10L, 3, 20.0),
                RowFactory.create(2L, 2L, 20L, 100, 5.0)
        );
        Dataset<Row> products = products(
                RowFactory.create(10L, "Widget", 20.0, 1L),
                RowFactory.create(20L, "Old Bestseller", 5.0, 1L)
        );

        Dataset<Row> result = BatchAnalytics.productPopularity(orders, items, products, NOW, HALF_LIFE_DAYS);

        double recentSmallSale = popularityOf(result, 10L);
        double oldBigSale = popularityOf(result, 20L);

        // 3 * 1.0 = 3.0 vs 100 * 0.25 = 25.0 - the old sale still wins here (its
        // volume is large enough to overcome the decay), but the point of this
        // test is the RATIO: decay demonstrably reduced the old sale's weight
        // to about a quarter of its raw quantity, not that recency always wins
        // regardless of volume (it doesn't, and shouldn't).
        assertTrue(oldBigSale < 100.0 * 0.30 && oldBigSale > 100.0 * 0.20,
                "expected ~25.0 (100 * ~0.25 decay), got " + oldBigSale);
        assertEquals(3.0, recentSmallSale, 1e-3);
    }

    @Test
    void onlyPaidOrders_shouldContributeToPopularity() {
        Dataset<Row> orders = orders(
                RowFactory.create(1L, 100L, 50.0, "DELIVERED", "PAID", NOW),
                RowFactory.create(2L, 101L, 20.0, "CANCELLED", "UNPAID", NOW),
                RowFactory.create(3L, 102L, 40.0, "PENDING", "UNPAID", NOW)
        );
        Dataset<Row> items = orderItems(
                RowFactory.create(1L, 1L, 10L, 2, 20.0),
                RowFactory.create(2L, 2L, 10L, 50, 20.0), // CANCELLED/UNPAID - must be excluded
                RowFactory.create(3L, 3L, 10L, 50, 20.0)  // PENDING/UNPAID - must be excluded
        );
        Dataset<Row> products = products(RowFactory.create(10L, "Widget", 20.0, 1L));

        Dataset<Row> result = BatchAnalytics.productPopularity(orders, items, products, NOW, HALF_LIFE_DAYS);

        // Only the PAID order's 2 units should count, not the 50+50 from unpaid/cancelled orders.
        assertEquals(2.0, popularityOf(result, 10L), 1e-6);
    }

    @Test
    void invalidHalfLife_shouldThrow() {
        Dataset<Row> orders = orders();
        Dataset<Row> items = orderItems();
        Dataset<Row> products = products(RowFactory.create(10L, "Widget", 20.0, 1L));

        assertThrows(IllegalArgumentException.class,
                () -> BatchAnalytics.productPopularity(orders, items, products, NOW, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> BatchAnalytics.productPopularity(orders, items, products, NOW, -5.0));
    }
}
