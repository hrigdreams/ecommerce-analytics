package com.ecommerce.analytics.spark.config;

import java.time.Instant;
import java.util.Properties;

/**
 * Central place for every environment-specific value both Spark jobs need.
 * Everything is read from environment variables, with low-latency local-dev defaults
 * that match the main Spring Boot app's application.properties.
 */
public final class SparkAppConfig {

    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    public final String kafkaBootstrapServers;
    public final String kafkaTopic;

    /** Distinct from the Spring app's "analytics-consumer" group - required so Spark never interferes with it. */
    public final String kafkaConsumerGroupId;

    public final String checkpointBaseLocation;

    /** Rows/events with occurredAt before this are DataGenerator's historical baseline. Null = now(). */
    public final Instant historicalCutoff;

    public final String windowDuration;
    public final String slideDuration;
    public final String watermarkDelay;
    public final String triggerInterval;
    public final String startingOffsets;

    /** See BatchAnalytics#productPopularity - after this many days, past quantity contributes half as much. */
    public final double popularityHalfLifeDays;

    private SparkAppConfig(Properties p) {
        this.dbUrl = env("DB_URL", "jdbc:postgresql://localhost:5432/ecommerce_analytics");
        this.dbUser = env("DB_USERNAME", "postgres");
        this.dbPassword = env("DB_PASSWORD", "admin");

        this.kafkaBootstrapServers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        this.kafkaTopic = env("SPARK_KAFKA_TOPIC", "ecommerce.domain.events.v1");
        this.kafkaConsumerGroupId = env("SPARK_KAFKA_GROUP_ID", "spark-streaming-analytics");

        this.checkpointBaseLocation = env("SPARK_CHECKPOINT_LOCATION", "/tmp/spark-analytics-checkpoints");

        String cutoff = env("SPARK_HISTORICAL_CUTOFF", null);
        this.historicalCutoff = cutoff != null ? Instant.parse(cutoff) : Instant.now();

        // Optimized streaming timing parameters for instant UI feedback
        this.windowDuration = env("SPARK_WINDOW_DURATION", "30 seconds");
        this.slideDuration = env("SPARK_SLIDE_DURATION", "30 seconds");
        this.watermarkDelay = env("SPARK_WATERMARK_DELAY", "10 seconds");
        this.triggerInterval = env("SPARK_TRIGGER_INTERVAL", "5 seconds");
        this.startingOffsets = env("SPARK_STARTING_OFFSETS", "latest");

        this.popularityHalfLifeDays = Double.parseDouble(env("SPARK_POPULARITY_HALF_LIFE_DAYS", "14"));
    }

    public static SparkAppConfig load() {
        return new SparkAppConfig(System.getProperties());
    }

    public java.util.Properties jdbcConnectionProperties() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", dbUser);
        props.setProperty("password", dbPassword);
        props.setProperty("driver", "org.postgresql.Driver");
        return props;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    @Override
    public String toString() {
        return "SparkAppConfig{dbUrl=%s, kafkaBootstrapServers=%s, kafkaTopic=%s, kafkaConsumerGroupId=%s, checkpointBaseLocation=%s, historicalCutoff=%s, popularityHalfLifeDays=%s}"
                .formatted(dbUrl, kafkaBootstrapServers, kafkaTopic, kafkaConsumerGroupId, checkpointBaseLocation, historicalCutoff, popularityHalfLifeDays);
    }
}