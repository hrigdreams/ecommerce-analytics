package com.ecommerce.analytics.spark.streaming;

import com.ecommerce.analytics.spark.config.SparkAppConfig;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeoutException;

/**
 * Kafka (ecommerce.domain.events.v1) -> Spark Structured Streaming ->
 * spark_streaming_* analytics tables.
 *
 * This is an ADDITIONAL Kafka consumer, not a replacement for the existing
 * Spring @KafkaListener consumer:
 *   - its own consumer group id (SparkAppConfig#kafkaConsumerGroupId,
 *     default "spark-streaming-analytics", NOT "analytics-consumer")
 *   - its own checkpoint directory, entirely separate from anything the
 *     Spring app tracks (it does not use the processed_events table -
 *     Spark's checkpoint is its own exactly-once offset/state store)
 *   - startingOffsets defaults to "latest", so a fresh run does not
 *     re-process years of history through a windowed streaming job; it
 *     only sees events produced from the moment it starts (i.e. live
 *     traffic - see LiveTrafficSimulator's docs on the historical cutoff)
 * Both consumers read the same topic independently and neither affects the
 * other's committed offsets, because Kafka tracks offsets per consumer
 * group.
 *
 * Run: see README.md ("Running the streaming job").
 */
public class SparkStreamingAnalyticsJob {

    private static final Logger log = LoggerFactory.getLogger(SparkStreamingAnalyticsJob.class);

    public static void main(String[] args) throws Exception {
        // See SparkTestSupport for why this AND spark.sql.session.timeZone (below)
        // are both needed - they fix two different timezone touchpoints (plain
        // java.sql.Timestamp/Date rendering vs. Spark SQL's own date/time functions).
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

        SparkAppConfig config = SparkAppConfig.load();
        log.info("Starting Spark streaming analytics job. Config: {}", config);

        try (SparkSession spark = SparkSession.builder()
                .appName("ecommerce-spark-streaming-analytics")
                .master(System.getProperty("spark.master", "local[*]"))
                // See SparkTestSupport for why this must be pinned: window()/to_timestamp()
                // otherwise interpret occurredAt in the HOST MACHINE's default timezone
                // instead of UTC, silently shifting every window boundary.
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate()) {

            spark.sparkContext().setLogLevel("WARN");

            Dataset<Row> kafkaRaw = spark.readStream()
                    .format("kafka")
                    .option("kafka.bootstrap.servers", config.kafkaBootstrapServers)
                    .option("subscribe", config.kafkaTopic)
                    .option("kafka.group.id", config.kafkaConsumerGroupId)
                    .option("startingOffsets", config.startingOffsets)
                    .option("failOnDataLoss", "false")
                    .load();

            Dataset<Row> parsed = StreamingAnalytics.parseEnvelope(kafkaRaw);

            StreamingQuery eventCounts = startJdbcSink(
                    StreamingAnalytics.eventCountsByWindow(parsed, config.windowDuration, config.watermarkDelay),
                    config, "spark_streaming_event_counts", "event-counts");

            StreamingQuery orderActivity = startJdbcSink(
                    StreamingAnalytics.orderActivityByWindow(parsed, config.windowDuration, config.watermarkDelay),
                    config, "spark_streaming_order_activity", "order-activity");

            StreamingQuery paidRevenue = startJdbcSink(
                    StreamingAnalytics.paidRevenueByWindow(parsed, config.windowDuration, config.watermarkDelay),
                    config, "spark_streaming_revenue", "paid-revenue");

            log.info("All streaming queries started. Window={} watermark={} trigger={} startingOffsets={}",
                    config.windowDuration, config.watermarkDelay, config.triggerInterval, config.startingOffsets);

            spark.streams().awaitAnyTermination();
            // If one query fails, surface why rather than let the JVM hang silently.
            for (StreamingQuery q : new StreamingQuery[]{eventCounts, orderActivity, paidRevenue}) {
                if (q.exception().isDefined()) {
                    log.error("Query {} terminated with exception", q.name(), q.exception().get());
                }
            }
        }
    }

    /**
     * append output mode: a window's row is only ever emitted once, after the
     * watermark passes its end - which is exactly what a plain JDBC INSERT
     * wants (no upsert/dedupe logic needed downstream).
     */
    private static StreamingQuery startJdbcSink(
            Dataset<Row> windowedAgg, SparkAppConfig config, String table, String queryName) throws TimeoutException {

        Properties jdbcProps = config.jdbcConnectionProperties();
        String checkpointLocation = config.checkpointBaseLocation + "/" + queryName;

        return windowedAgg.writeStream()
                .queryName(queryName)
                .outputMode("append")
                .option("checkpointLocation", checkpointLocation)
                .trigger(Trigger.ProcessingTime(config.triggerInterval))
                .foreachBatch((batchDf, batchId) -> {
                    long rows = batchDf.count();
                    if (rows > 0) {
                        batchDf.write().mode(SaveMode.Append).jdbc(config.dbUrl, table, jdbcProps);
                        log.info("[{}] batch {}: wrote {} row(s) to {}", queryName, batchId, rows, table);
                    }
                })
                .start();
    }
}
