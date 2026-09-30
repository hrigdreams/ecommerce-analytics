package com.ecommerce.analytics.spark;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

// not touching this
public abstract class SparkTestSupport {

    protected static SparkSession spark;

    @BeforeAll
    static void startSpark() {

        spark = SparkSession.builder()
                .appName("spark-analytics-tests")
                .master("local[2]")
                .config("spark.sql.shuffle.partitions", "2")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();

// rough idea
        spark.conf().set("spark.sql.session.timeZone", "UTC");
    }

    @AfterAll
    static void stopSpark() {

        if (spark != null) {
            spark.stop();
        }
    }
}