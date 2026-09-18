package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.product.ProductCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCreatedEventHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private final ProductCreatedEventHandler handler =
            new ProductCreatedEventHandler(objectMapper);

    @Test
    void shouldSupportProductCreatedEvent() {
        assertEquals(
                EventType.PRODUCT_CREATED,
                handler.supportedEventType()
        );
    }

    @Test
    void shouldDeserializeProductCreatedPayload() {
        JsonNode payload = objectMapper.createObjectNode()
                .put("productId", 100L)
                .put("name", "Test Product")
                .put("price", 99.99)
                .put("categoryId", 10L);

        assertDoesNotThrow(() -> handler.handle(payload));
    }
}