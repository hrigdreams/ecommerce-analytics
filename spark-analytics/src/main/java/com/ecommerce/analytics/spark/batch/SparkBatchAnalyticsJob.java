package com.ecommerce.analytics.spark.batch;

import com.ecommerce.analytics.spark.config.SparkAppConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Properties;

/**
 * Historical PostgreSQL -> Spark Batch -> spark_batch_* analytics tables.
 *
 * Reads only rows with created_at < historicalCutoff (DataGenerator's
 * backdated baseline plus any live simulator activity that has since aged
 * past the cutoff), computes distributed aggregates with the Spark SQL
 * DataFrame API, and overwrites its own spark_batch_* tables - never the
 * Spring app's product_analytics / order_analytics / etc. Those stay owned
 * by the existing Kafka consumer; this job never writes to them, so there
 * is no risk of two writers racing on the same row.
 *
 * Note on spark_batch_product_popularity specifically: unlike the other
 * spark_batch_* tables (all filtered to created_at < historicalCutoff -
 * "historical" analytics by definition), popularity is read over orders/
 * order_items WITHOUT that cutoff filter. A recency-weighted ranking signal
 * that only ever saw data older than the cutoff would decay towards zero
 * for every product as real time moves past that fixed point - the whole
 * point of this feature is "what's popular right now", so it must see
 * today's orders too, not just the frozen historical baseline. This does
 * not reintroduce Spark Batch/Streaming double-counting (the concern the
 * historicalCutoff concept exists to prevent): popularity is a derived
 * score written to its OWN table by Spark Batch alone - Spark Streaming
 * does not compute or touch popularityScore anywhere - so there is no
 * second writer to double-count against.
 *
 * PHASE 1, item 4 (daily revenue rollups should include today's streamed
 * activity): dailyRevenueTrend now follows that exact same precedent -
 * it's computed from {@code allOrders} (unfiltered) instead of the
 * historicalCutoff-filtered {@code orders}. Before this change, a day's bar
 * in the revenue trend chart would stay frozen at its historical-baseline
 * value even while the simulator/Kafka pipeline kept adding paid orders for
 * "today" in the live Postgres table - re-running this batch job wouldn't
 * move today's number at all, since every order created after the cutoff
 * was filtered out before dailyRevenueTrend ever saw it. productPerformance,
 * categoryPerformance, customerBehavior, geoPerformance and
 * hourOfDayPattern are deliberately left on the filtered {@code orders}
 * Dataset, unchanged - those are still meant to answer "how did the
 * historical baseline look", which is a different, still-useful question,
 * and the request's item 4 was specifically about the daily revenue rollup,
 * not every spark_batch_* table. Widening all of them to allOrders in one
 * sweep was considered and rejected: it would silently change what every
 * other chart means (and could double-count against spark_streaming_*'s own
 * live numbers in those sections of the dashboard) for a request that was
 * about revenue trend specifically.
 *
 * Run: see README.md ("Running the batch job").
 */
public class SparkBatchAnalyticsJob {

    private static final Logger log = LoggerFactory.getLogger(SparkBatchAnalyticsJob.class);

    public static void main(String[] args) {
        // See SparkTestSupport for why this AND spark.sql.session.timeZone (below)
        // are both needed - they fix two different timezone touchpoints (plain
        // java.sql.Timestamp/Date rendering vs. Spark SQL's own date/time functions).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

        SparkAppConfig config = SparkAppConfig.load();
        log.info("Starting Spark batch analytics job. Config: {}", config);

        try (SparkSession spark = SparkSession.builder()
                .appName("ecommerce-spark-batch-analytics")
                .master(System.getProperty("spark.master", "local[*]"))
                // See SparkTestSupport for why this must be pinned: hour()/to_date()
                // otherwise use the HOST MACHINE's default timezone, making results
                // depend on where the job happens to run rather than the data itself.
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate()) {

            run(spark, config);
        }
    }

    static void run(SparkSession spark, SparkAppConfig config) {
        Properties jdbcProps = config.jdbcConnectionProperties();
        Timestamp cutoff = Timestamp.from(config.historicalCutoff);
        Timestamp now = Timestamp.from(java.time.Instant.now());

        Dataset<Row> orders = readFiltered(spark, config.dbUrl, jdbcProps,
                "orders", "created_at", cutoff);
        // Unfiltered (no historicalCutoff) - see class doc above for why both
        // popularity AND (as of Phase 1, item 4) the daily revenue trend need
        // to see all orders, not just the pre-cutoff historical baseline.
        Dataset<Row> allOrders = spark.read().jdbc(config.dbUrl, "orders", jdbcProps);
        Dataset<Row> orderItems = spark.read().jdbc(config.dbUrl, "order_items", jdbcProps);
        Dataset<Row> products = spark.read().jdbc(config.dbUrl, "products", jdbcProps);
        Dataset<Row> categories = spark.read().jdbc(config.dbUrl, "categories", jdbcProps);
        Dataset<Row> users = spark.read().jdbc(config.dbUrl, "users", jdbcProps);

        orders.cache();
        long orderCount = orders.count();
        log.info("Historical orders (created_at < {}): {}", config.historicalCutoff, orderCount);

        Dataset<Row> productPerformance = BatchAnalytics.productPerformance(orders, orderItems, products);
        Dataset<Row> categoryPerformance = BatchAnalytics.categoryPerformance(productPerformance, categories);
        Dataset<Row> customerBehavior = BatchAnalytics.customerBehavior(orders);
        // PHASE 1, item 4: allOrders (unfiltered), not orders (historical-only) -
        // see class javadoc for the rationale and why the other Datasets below
        // deliberately stay on the filtered `orders` Dataset.
        Dataset<Row> dailyRevenueTrend = BatchAnalytics.dailyRevenueTrend(allOrders);
        Dataset<Row> geoPerformance = BatchAnalytics.geoPerformance(orders, users);
        Dataset<Row> hourOfDayPattern = BatchAnalytics.hourOfDayPattern(orders);
        Dataset<Row> productPopularity = BatchAnalytics.productPopularity(
                allOrders, orderItems, products, now, config.popularityHalfLifeDays);

        write(productPerformance, config, jdbcProps, "spark_batch_product_performance");
        write(categoryPerformance, config, jdbcProps, "spark_batch_category_performance");
        write(customerBehavior, config, jdbcProps, "spark_batch_customer_behavior");
        write(dailyRevenueTrend, config, jdbcProps, "spark_batch_revenue_trend");
        write(geoPerformance, config, jdbcProps, "spark_batch_geo_performance");
        write(hourOfDayPattern, config, jdbcProps, "spark_batch_hourly_pattern");
        write(productPopularity, config, jdbcProps, "spark_batch_product_popularity");

        orders.unpersist();
        log.info("Spark batch analytics job finished successfully.");
    }

    /** Pushes the created_at < cutoff filter down into the SQL itself (via a subquery "table"), not into Spark after the fact. */
    private static Dataset<Row> readFiltered(
            SparkSession spark, String url, Properties props, String table, String tsColumn, Timestamp cutoff) {

        String subquery = "(SELECT * FROM " + table + " WHERE " + tsColumn + " < '" + cutoff.toInstant() + "') AS " + table + "_hist";
        return spark.read().jdbc(url, subquery, props);
    }

    private static void write(Dataset<Row> df, SparkAppConfig config, Properties jdbcProps, String table) {
        // Not logging df.count() here on purpose: it would force a second full
        // computation of the DataFrame just for a log line. Row counts are
        // visible via `SELECT COUNT(*) FROM <table>` after the job finishes.
        df.write().mode(SaveMode.Overwrite).jdbc(config.dbUrl, table, jdbcProps);
        log.info("Wrote results to {}", table);
    }
}
