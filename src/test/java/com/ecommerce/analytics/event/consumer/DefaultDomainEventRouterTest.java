package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.ecommerce.analytics.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultDomainEventRouterTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    private ProcessedEventRepository processedEventRepository;
    private DomainEventHandler productCreatedHandler;
    private DefaultDomainEventRouter router;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        productCreatedHandler = mock(DomainEventHandler.class);
        when(productCreatedHandler.supportedEventType())
                .thenReturn(EventType.PRODUCT_CREATED);

        router = new DefaultDomainEventRouter(
                List.of(productCreatedHandler),
                processedEventRepository
        );
    }

    private EventEnvelope<JsonNode> event(EventType type, UUID id) {
        return new EventEnvelope<>(
                id,
                type,
                Instant.now(),
                "Product",
                100L,
                1,
                objectMapper.createObjectNode()
        );
    }

    @Test
    void shouldRouteNewEventToHandler() {
        when(processedEventRepository.markProcessed(anyString())).thenReturn(1);
        EventEnvelope<JsonNode> event = event(EventType.PRODUCT_CREATED, UUID.randomUUID());

        router.route(event);

        verify(productCreatedHandler).handle(event);
    }

    @Test
    void shouldSkipDuplicateEvent() {
        when(processedEventRepository.markProcessed(anyString())).thenReturn(0);

        router.route(event(EventType.PRODUCT_CREATED, UUID.randomUUID()));

        verify(productCreatedHandler, never()).handle(any());
    }

    @Test
    void shouldIgnoreEventTypeWithoutHandler() {
        EventEnvelope<JsonNode> event = event(EventType.USER_CREATED, UUID.randomUUID());

        assertDoesNotThrow(() -> router.route(event));

        verifyNoInteractions(processedEventRepository);
        verify(productCreatedHandler, never()).handle(any());
    }

    @Test
    void shouldRejectNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> router.route(null));
    }

    @Test
    void shouldRejectNullEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> router.route(event(null, UUID.randomUUID()))
        );
    }

    @Test
    void shouldRejectNullEventId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> router.route(event(EventType.PRODUCT_CREATED, null))
        );
    }

    @Test
    void shouldRejectTwoHandlersForSameEventType() {
        DomainEventHandler duplicate = mock(DomainEventHandler.class);
        when(duplicate.supportedEventType()).thenReturn(EventType.PRODUCT_CREATED);

        assertThrows(
                IllegalStateException.class,
                () -> new DefaultDomainEventRouter(
                        List.of(productCreatedHandler, duplicate),
                        processedEventRepository
                )
        );
    }
}
