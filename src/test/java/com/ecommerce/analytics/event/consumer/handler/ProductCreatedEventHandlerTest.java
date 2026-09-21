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

class ProductCreatedEventHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private final ProductAnalyticsService service = mock(ProductAnalyticsService.class);

    private final ProductCreatedEventHandler handler =
            new ProductCreatedEventHandler(objectMapper, service);

    @Test
    void shouldSupportProductCreatedEvent() {
        assertEquals(EventType.PRODUCT_CREATED, handler.supportedEventType());
    }

    @Test
    void shouldUpsertProductAnalyticsFromPayload() {
        Instant occurredAt = Instant.parse("2026-09-18T10:00:00Z");

        JsonNode payload = objectMapper.createObjectNode()
                .put("productId", 100L)
                .put("name", "Test Product")
                .put("price", 99.99)
                .put("categoryId", 10L);

        EventEnvelope<JsonNode> event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventType.PRODUCT_CREATED,
                occurredAt,
                "Product",
                100L,
                1,
                payload
        );

        handler.handle(event);

        verify(service).upsertProduct(100L, "Test Product", 10L, 99.99, occurredAt);
    }
}
