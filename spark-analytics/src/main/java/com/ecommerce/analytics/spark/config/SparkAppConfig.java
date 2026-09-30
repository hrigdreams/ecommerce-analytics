package com.ecommerce.analytics.spark.config;

import java.time.Instant;
import java.util.Properties;

// local note
public final class SparkAppConfig {

    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;

    public final String kafkaBootstrapServers;
    public final String kafkaTopic;

// small reminder
    public final String kafkaConsumerGroupId;

    public final String checkpointBaseLocation;

// not touching this
    public final Instant historicalCutoff;

    public final String windowDuration;
    public final String slideDuration;
    public final String watermarkDelay;
    public final String triggerInterval;
    public final String startingOffsets;

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

        this.windowDuration = env("SPARK_WINDOW_DURATION", "1 minute");
        this.slideDuration = env("SPARK_SLIDE_DURATION", "1 minute");
        this.watermarkDelay = env("SPARK_WATERMARK_DELAY", "2 minutes");
        this.triggerInterval = env("SPARK_TRIGGER_INTERVAL", "30 seconds");
        this.startingOffsets = env("SPARK_STARTING_OFFSETS", "latest");
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
        return "SparkAppConfig{dbUrl=%s, kafkaBootstrapServers=%s, kafkaTopic=%s, kafkaConsumerGroupId=%s, checkpointBaseLocation=%s, historicalCutoff=%s}"
                .formatted(dbUrl, kafkaBootstrapServers, kafkaTopic, kafkaConsumerGroupId, checkpointBaseLocation, historicalCutoff);
    }
}
