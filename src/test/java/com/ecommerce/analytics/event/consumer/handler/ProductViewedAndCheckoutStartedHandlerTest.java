package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.service.FunnelAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductViewedAndCheckoutStartedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FunnelAnalyticsService funnelAnalyticsService = mock(FunnelAnalyticsService.class);
    private final Instant occurredAt = Instant.parse("2026-09-23T10:00:00Z");

    private EventEnvelope<JsonNode> envelope(EventType type, JsonNode payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, occurredAt, "Product", 10L, 1, payload);
    }

    @Test
    void productViewedHandler_shouldRecordFunnelStage() {
        ProductViewedEventHandler handler = new ProductViewedEventHandler(funnelAnalyticsService);
        assertEquals(EventType.PRODUCT_VIEWED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode()
                .put("productId", 10L)
                .put("productName", "Phone");

        handler.handle(envelope(EventType.PRODUCT_VIEWED, payload));

        verify(funnelAnalyticsService).recordProductViewed(occurredAt);
    }

    @Test
    void checkoutStartedHandler_shouldRecordFunnelStage() {
        CheckoutStartedEventHandler handler = new CheckoutStartedEventHandler(funnelAnalyticsService);
        assertEquals(EventType.CHECKOUT_STARTED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode()
                .put("userId", 7L)
                .put("itemCount", 3);

        handler.handle(envelope(EventType.CHECKOUT_STARTED, payload));

        verify(funnelAnalyticsService).recordCheckoutStarted(occurredAt);
    }
}
