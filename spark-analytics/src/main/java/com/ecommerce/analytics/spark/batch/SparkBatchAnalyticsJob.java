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

        Dataset<Row> orders = readFiltered(spark, config.dbUrl, jdbcProps,
                "orders", "created_at", cutoff);
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
        Dataset<Row> dailyRevenueTrend = BatchAnalytics.dailyRevenueTrend(orders);
        Dataset<Row> geoPerformance = BatchAnalytics.geoPerformance(orders, users);
        Dataset<Row> hourOfDayPattern = BatchAnalytics.hourOfDayPattern(orders);

        write(productPerformance, config, jdbcProps, "spark_batch_product_performance");
        write(categoryPerformance, config, jdbcProps, "spark_batch_category_performance");
        write(customerBehavior, config, jdbcProps, "spark_batch_customer_behavior");
        write(dailyRevenueTrend, config, jdbcProps, "spark_batch_revenue_trend");
        write(geoPerformance, config, jdbcProps, "spark_batch_geo_performance");
        write(hourOfDayPattern, config, jdbcProps, "spark_batch_hourly_pattern");

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
