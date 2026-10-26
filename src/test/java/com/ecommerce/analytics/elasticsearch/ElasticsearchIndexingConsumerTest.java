package com.ecommerce.analytics.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ElasticsearchIndexingConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ProductIndexingService productIndexingService = mock(ProductIndexingService.class);
    private final ElasticsearchIndexingConsumer consumer =
            new ElasticsearchIndexingConsumer(objectMapper, productIndexingService);

    private String envelope(String eventType, String aggregateType, String payload) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-09-24T10:00:00Z",
                  "aggregateType": "%s",
                  "aggregateId": 10,
                  "version": 1,
                  "payload": %s
                }
                """.formatted(UUID.randomUUID(), eventType, aggregateType, payload);
    }

    private String envelope(String eventType, String payload) {
        return envelope(eventType, "Product", payload);
    }

    @Test
    void shouldRouteProductCreatedToUpsert() {
        consumer.consume(envelope("PRODUCT_CREATED",
                "{\"productId\":10,\"name\":\"Phone\",\"price\":499.99,\"categoryId\":1}"));

        verify(productIndexingService).upsertProduct(anyString(), eq(10L), eq("Phone"), eq(499.99), eq(1L), any());
    }

    @Test
    void shouldRouteProductDeletedToDelete() {
        consumer.consume(envelope("PRODUCT_DELETED", "{\"productId\":10}"));

        verify(productIndexingService).deleteProduct(anyString(), eq(10L), any());
    }

    @Test
    void shouldRouteReviewCreatedToRecompute() {
        consumer.consume(envelope("REVIEW_CREATED",
                "{\"reviewId\":1,\"userId\":7,\"productId\":10,\"rating\":5,\"comment\":\"Great\",\"createdAt\":\"2026-09-24T10:00:00Z\"}"));

        verify(productIndexingService).recomputeReviewAggregate(anyString(), eq(10L), any());
    }

    // ------------------------------------------------------------------
    // ORDER_CREATED -> processOrderCreated (real-sales ranking signal).
    // ------------------------------------------------------------------

    @Test
    void shouldRouteOrderCreatedToProcessOrderCreated() {
        String payload = """
                {
                  "orderId": 500,
                  "userId": 7,
                  "status": "PENDING",
                  "paymentStatus": "UNPAID",
                  "totalAmount": 119.97,
                  "createdAt": "2026-09-24T10:00:00Z",
                  "items": [
                    {"orderItemId": 1, "productId": 10, "productName": "Phone", "quantity": 2, "unitPrice": 49.99},
                    {"orderItemId": 2, "productId": 20, "productName": "Case", "quantity": 1, "unitPrice": 19.99}
                  ]
                }
                """;

        consumer.consume(envelope("ORDER_CREATED", "Order", payload));

        verify(productIndexingService).processOrderCreated(
                anyString(),
                org.mockito.ArgumentMatchers.argThat(items ->
                        items.size() == 2
                                && items.get(0).getProductId().equals(10L)
                                && items.get(0).getQuantity() == 2
                                && items.get(1).getProductId().equals(20L)
                                && items.get(1).getQuantity() == 1),
                any());
    }

    @Test
    void shouldRouteOrderCreatedWithNoItems_toProcessOrderCreatedWithEmptyList() {
        String payload = """
                {
                  "orderId": 501,
                  "userId": 7,
                  "status": "PENDING",
                  "paymentStatus": "UNPAID",
                  "totalAmount": 0,
                  "createdAt": "2026-09-24T10:00:00Z",
                  "items": []
                }
                """;

        consumer.consume(envelope("ORDER_CREATED", "Order", payload));

        verify(productIndexingService).processOrderCreated(anyString(), eq(List.of()), any());
    }

    // ------------------------------------------------------------------
    // ORDER_STATUS_CHANGED -> processOrderCancelled, ONLY for newStatus
    // CANCELLED. Every other status transition must be ignored.
    // ------------------------------------------------------------------

    @Test
    void shouldRouteOrderStatusChangedToCancelled_toProcessOrderCancelled() {
        String payload = """
                {"orderId": 500, "previousStatus": "PENDING", "newStatus": "CANCELLED"}
                """;

        consumer.consume(envelope("ORDER_STATUS_CHANGED", "Order", payload));

        verify(productIndexingService).processOrderCancelled(anyString(), eq(500L), any());
    }

    @Test
    void shouldRouteOrderStatusChangedToCancelled_caseInsensitively() {
        String payload = """
                {"orderId": 500, "previousStatus": "PENDING", "newStatus": "cancelled"}
                """;

        consumer.consume(envelope("ORDER_STATUS_CHANGED", "Order", payload));

        verify(productIndexingService).processOrderCancelled(anyString(), eq(500L), any());
    }

    @Test
    void shouldIgnoreOrderStatusChangedToNonCancelledStatuses() {
        for (String status : List.of("PROCESSING", "SHIPPED", "DELIVERED", "PENDING")) {
            String payload = "{\"orderId\": 500, \"previousStatus\": \"PENDING\", \"newStatus\": \"" + status + "\"}";
            consumer.consume(envelope("ORDER_STATUS_CHANGED", "Order", payload));
        }

        verify(productIndexingService, never()).processOrderCancelled(anyString(), any(), any());
    }

    // ------------------------------------------------------------------
    // Existing PRODUCT_*/REVIEW_* routing and irrelevant-event handling
    // must remain intact.
    // ------------------------------------------------------------------

    @Test
    void shouldIgnoreIrrelevantEventTypesWithoutError() {
        assertDoesNotThrow(() -> consumer.consume(envelope("PAYMENT_CREATED", "Payment", "{}")));

        verifyNoInteractions(productIndexingService);
    }

    @Test
    void shouldThrowOnUnparseableMessage_soKafkaDoesNotAckIt() {
        assertThrows(RuntimeException.class, () -> consumer.consume("not valid json"));

        verifyNoInteractions(productIndexingService);
    }
}
