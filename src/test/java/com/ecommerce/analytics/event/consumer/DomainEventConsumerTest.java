package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.ecommerce.analytics.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private final ProcessedEventRepository processedEventRepository =
            mock(ProcessedEventRepository.class);

    private final DomainEventHandler productCreatedHandler =
            new DomainEventHandler() {

                @Override
                public EventType supportedEventType() {
                    return EventType.PRODUCT_CREATED;
                }

                @Override
                public void handle(EventEnvelope<JsonNode> event) {
                    // No-op for consumer test.
                }
            };

    private final DomainEventRouter router;
    private final DomainEventConsumer consumer;

    DomainEventConsumerTest() {
        when(processedEventRepository.markProcessed(anyString())).thenReturn(1);
        this.router = new DefaultDomainEventRouter(
                List.of(productCreatedHandler),
                processedEventRepository
        );
        this.consumer = new DomainEventConsumer(objectMapper, router);
    }

    @Test
    void shouldConsumeValidDomainEvent() {
        String message = """
                {
                  "eventId": "%s",
                  "eventType": "PRODUCT_CREATED",
                  "occurredAt": "2026-09-18T10:00:00Z",
                  "aggregateType": "Product",
                  "aggregateId": 100,
                  "version": 1,
                  "payload": {
                    "productId": 100,
                    "name": "Test Product",
                    "price": 99.99,
                    "categoryId": 10
                  }
                }
                """.formatted(UUID.randomUUID());

        assertDoesNotThrow(() -> consumer.consume(message));
    }

    @Test
    void shouldRejectInvalidDomainEvent() {
        String message = """
                {
                  "eventType": "INVALID_EVENT"
                }
                """;

        assertThrows(
                IllegalStateException.class,
                () -> consumer.consume(message)
        );
    }

    @Test
    void shouldRejectEventWithoutEventType() {
        String message = """
                {
                  "eventId": "%s",
                  "occurredAt": "2026-09-18T10:00:00Z",
                  "aggregateType": "Product",
                  "aggregateId": 100,
                  "version": 1,
                  "payload": {}
                }
                """.formatted(UUID.randomUUID());

        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(message)
        );
    }
}
