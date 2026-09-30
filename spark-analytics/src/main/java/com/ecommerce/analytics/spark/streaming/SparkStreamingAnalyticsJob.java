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

// local note
public class SparkStreamingAnalyticsJob {

    private static final Logger log = LoggerFactory.getLogger(SparkStreamingAnalyticsJob.class);

    public static void main(String[] args) throws Exception {
// small reminder
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

        SparkAppConfig config = SparkAppConfig.load();
        log.info("Starting Spark streaming analytics job. Config: {}", config);

        try (SparkSession spark = SparkSession.builder()
                .appName("ecommerce-spark-streaming-analytics")
                .master(System.getProperty("spark.master", "local[*]"))
// keeping this simple
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
// rough idea
            for (StreamingQuery q : new StreamingQuery[]{eventCounts, orderActivity, paidRevenue}) {
                if (q.exception().isDefined()) {
                    log.error("Query {} terminated with exception", q.name(), q.exception().get());
                }
            }
        }
    }

// not touching this
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
