package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.service.ProductAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductUpdatedAndDeletedEventHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private final ProductAnalyticsService service = mock(ProductAnalyticsService.class);

    private final Instant occurredAt = Instant.parse("2026-09-18T11:00:00Z");

    private EventEnvelope<JsonNode> envelope(EventType type, JsonNode payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(), type, occurredAt, "Product", 100L, 1, payload
        );
    }

    @Test
    void updatedHandlerShouldUpsert() {
        ProductUpdatedEventHandler handler =
                new ProductUpdatedEventHandler(objectMapper, service);

        assertEquals(EventType.PRODUCT_UPDATED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode()
                .put("productId", 100L)
                .put("name", "Renamed")
                .put("price", 120.5)
                .put("categoryId", 11L);

        handler.handle(envelope(EventType.PRODUCT_UPDATED, payload));

        verify(service).upsertProduct(100L, "Renamed", 11L, 120.5, occurredAt);
    }

    @Test
    void deletedHandlerShouldMarkDeleted() {
        ProductDeletedEventHandler handler =
                new ProductDeletedEventHandler(objectMapper, service);

        assertEquals(EventType.PRODUCT_DELETED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode().put("productId", 100L);

        handler.handle(envelope(EventType.PRODUCT_DELETED, payload));

        verify(service).markDeleted(100L, occurredAt);
    }
}
