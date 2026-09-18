package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultDomainEventRouterTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    private final DomainEventHandler productCreatedHandler =
            new DomainEventHandler() {

                @Override
                public EventType supportedEventType() {
                    return EventType.PRODUCT_CREATED;
                }

                @Override
                public void handle(JsonNode payload) {
                }
            };

    private final DefaultDomainEventRouter router =
            new DefaultDomainEventRouter(
                    List.of(productCreatedHandler)
            );

    @Test
    void shouldRouteKnownEventType() {
        EventEnvelope<JsonNode> event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventType.PRODUCT_CREATED,
                Instant.now(),
                "Product",
                100L,
                1,
                objectMapper.createObjectNode()
        );

        assertDoesNotThrow(() -> router.route(event));
    }

    @Test
    void shouldRejectNullEvent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> router.route(null)
        );
    }

    @Test
    void shouldRejectNullEventType() {
        EventEnvelope<JsonNode> event = new EventEnvelope<>(
                UUID.randomUUID(),
                null,
                Instant.now(),
                "Product",
                100L,
                1,
                objectMapper.createObjectNode()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> router.route(event)
        );
    }

    @Test
    void shouldRejectUnhandledEventType() {
        EventEnvelope<JsonNode> event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventType.USER_CREATED,
                Instant.now(),
                "User",
                1L,
                1,
                objectMapper.createObjectNode()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> router.route(event)
        );
    }
}