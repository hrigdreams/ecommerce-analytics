package com.ecommerce.analytics.spark.streaming;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// quick note
class StreamingAnalyticsTest extends SparkTestSupport {

// small reminder
    private Dataset<Row> kafkaValues(String... jsonMessages) {
        StructType schema = new StructType(new StructField[]{
                new StructField("value", DataTypes.BinaryType, true, Metadata.empty())
        });
        List<Row> rows = new java.util.ArrayList<>();
        for (String json : jsonMessages) {
            rows.add(RowFactory.create((Object) json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return spark.createDataFrame(rows, schema);
    }

    private static String orderCreated(String eventId, String occurredAt, long orderId, double total) {
        return """
                {"eventId":"%s","eventType":"ORDER_CREATED","occurredAt":"%s",
                 "aggregateType":"Order","aggregateId":%d,"version":1,
                 "payload":{"orderId":%d,"userId":7,"status":"PENDING","paymentStatus":"UNPAID",
                            "totalAmount":%s,"createdAt":"%s","items":[]}}
                """.formatted(eventId, occurredAt, orderId, orderId, total, occurredAt);
    }

    private static String paymentCreated(String eventId, String occurredAt, long orderId, double amount, String status) {
        return """
                {"eventId":"%s","eventType":"PAYMENT_CREATED","occurredAt":"%s",
                 "aggregateType":"Payment","aggregateId":%d,"version":1,
                 "payload":{"paymentId":1,"orderId":%d,"paymentMethod":"CARD","status":"%s",
                            "amount":%s,"transactionId":"txn-1","createdAt":"%s"}}
                """.formatted(eventId, occurredAt, orderId, orderId, status, amount, occurredAt);
    }

    @Test
    void parseEnvelope_shouldExtractEnvelopeAndPayloadFields() {
        Dataset<Row> raw = kafkaValues(
                orderCreated("e1", "2026-09-24T10:00:05Z", 100L, 59.98)
        );
        Row row = StreamingAnalytics.parseEnvelope(raw).collectAsList().get(0);

        assertEquals("e1", row.getAs("event_id"));
        assertEquals("ORDER_CREATED", row.getAs("event_type"));
        assertEquals("Order", row.getAs("aggregate_type"));
        assertEquals(100L, row.<Long>getAs("aggregate_id"));
        assertEquals(59.98, row.<Double>getAs("order_total_amount"), 0.001);
// just a note
        assertEquals(Timestamp.from(java.time.Instant.parse("2026-09-24T10:00:05Z")), row.getAs("event_time"));
    }

    @Test
    void parseEnvelope_shouldDropMessagesWithoutAParseableEventIdOrTime() {
        Dataset<Row> raw = kafkaValues("{\"not\":\"an envelope\"}");
        assertEquals(0, StreamingAnalytics.parseEnvelope(raw).count());
    }

    @Test
    void eventCountsByWindow_shouldBucketByOneMinuteTumblingWindowAndEventType() {
        Dataset<Row> raw = kafkaValues(
                orderCreated("e1", "2026-09-24T10:00:05Z", 100L, 10.0),
                orderCreated("e2", "2026-09-24T10:00:45Z", 101L, 10.0), // same minute as e1
                orderCreated("e3", "2026-09-24T10:01:05Z", 102L, 10.0), // next minute
                paymentCreated("e4", "2026-09-24T10:00:20Z", 100L, 10.0, "PAID") // same minute as e1, different type
        );
        Dataset<Row> parsed = StreamingAnalytics.parseEnvelope(raw);
        List<Row> result = StreamingAnalytics.eventCountsByWindow(parsed, "1 minute", "2 minutes").collectAsList();

        Map<String, Long> byTypeAndMinute = result.stream().collect(Collectors.toMap(
                r -> {
                    Timestamp windowStart = r.getAs("window_start");
                    return r.getAs("event_type") + "@" + windowStart.toInstant();
                },
                r -> r.getAs("event_count")
        ));

        assertEquals(2L, byTypeAndMinute.get("ORDER_CREATED@2026-09-24T10:00:00Z"));
        assertEquals(1L, byTypeAndMinute.get("ORDER_CREATED@2026-09-24T10:01:00Z"));
        assertEquals(1L, byTypeAndMinute.get("PAYMENT_CREATED@2026-09-24T10:00:00Z"));
    }

    @Test
    void orderActivityByWindow_shouldSumTotalAmountOfOrderCreatedEventsOnly() {
        Dataset<Row> raw = kafkaValues(
                orderCreated("e1", "2026-09-24T10:00:05Z", 100L, 25.50),
                orderCreated("e2", "2026-09-24T10:00:45Z", 101L, 14.50),
                paymentCreated("e3", "2026-09-24T10:00:10Z", 100L, 25.50, "PAID") // must NOT count here
        );
        Dataset<Row> parsed = StreamingAnalytics.parseEnvelope(raw);
        List<Row> result = StreamingAnalytics.orderActivityByWindow(parsed, "1 minute", "2 minutes").collectAsList();

        assertEquals(1, result.size());
        Row window = result.get(0);
        assertEquals(2L, window.<Long>getAs("orders_created"));
        assertEquals(40.00, window.getDecimal(window.fieldIndex("gross_amount")).doubleValue(), 0.001);
    }

    @Test
    void paidRevenueByWindow_shouldOnlyCountPaymentsWithStatusPaid() {
        Dataset<Row> raw = kafkaValues(
                paymentCreated("e1", "2026-09-24T10:00:05Z", 100L, 30.0, "PAID"),
                paymentCreated("e2", "2026-09-24T10:00:10Z", 101L, 15.0, "FAILED"),
                paymentCreated("e3", "2026-09-24T10:00:15Z", 102L, 20.0, "PAID")
        );
        Dataset<Row> parsed = StreamingAnalytics.parseEnvelope(raw);
        List<Row> result = StreamingAnalytics.paidRevenueByWindow(parsed, "1 minute", "2 minutes").collectAsList();

        assertEquals(1, result.size());
        Row window = result.get(0);
        assertEquals(2L, window.<Long>getAs("paid_count")); // FAILED payment excluded
        assertEquals(50.00, window.getDecimal(window.fieldIndex("paid_revenue")).doubleValue(), 0.001);
    }
}
