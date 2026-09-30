package com.ecommerce.analytics.spark;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/** One shared local[*] SparkSession for the whole test run - creating a new one per test class is slow. */
public abstract class SparkTestSupport {

    protected static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        // Fixes the SAME class of bug as spark.sql.session.timeZone below, but for
        // a different layer: java.sql.Timestamp.toString() (used when building
        // assertion values/map keys in these tests) is rendered using the JVM's
        // DEFAULT timezone, which is completely separate from Spark's own session
        // timeZone config. Without pinning this too, test assertions that compare
        // rendered strings are only correct on a UTC host machine.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

        spark = SparkSession.builder()
                .appName("spark-analytics-tests")
                .master("local[2]")
                .config("spark.sql.shuffle.partitions", "2")
                .config("spark.ui.enabled", "false")
                // Pinned to UTC deliberately: occurredAt (EventEnvelope) is a true
                // UTC Instant end-to-end, and hour()/to_date()/window() all operate
                // in Spark's SESSION timezone, not the JVM/host machine's default.
                // Without this, the exact same code produces different bucket
                // boundaries depending on which timezone the machine running it is
                // in - which is what caused the original failures (run on a
                // UTC+5:45 host, no pin -> everything silently shifted by 5:45).
                // See SparkAppConfig for the identical pin used by both real jobs.
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }
}
