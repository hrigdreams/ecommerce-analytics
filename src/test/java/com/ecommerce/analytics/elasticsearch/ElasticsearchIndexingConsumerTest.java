package com.ecommerce.analytics.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

    private String envelope(String eventType, String payload) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-09-24T10:00:00Z",
                  "aggregateType": "Product",
                  "aggregateId": 10,
                  "version": 1,
                  "payload": %s
                }
                """.formatted(UUID.randomUUID(), eventType, payload);
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

    @Test
    void shouldIgnoreIrrelevantEventTypesWithoutError() {
        assertDoesNotThrow(() -> consumer.consume(envelope("ORDER_CREATED", "{}")));

        verifyNoInteractions(productIndexingService);
    }

    @Test
    void shouldThrowOnUnparseableMessage_soKafkaDoesNotAckIt() {
        assertThrows(RuntimeException.class, () -> consumer.consume("not valid json"));

        verifyNoInteractions(productIndexingService);
    }
}
