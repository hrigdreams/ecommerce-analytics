package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartUpdatedEvent;
import com.ecommerce.analytics.service.CartAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CartUpdatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CartAnalyticsService cartAnalyticsService;

    public CartUpdatedEventHandler(
            ObjectMapper objectMapper,
            CartAnalyticsService cartAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.cartAnalyticsService = cartAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CART_UPDATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CartUpdatedEvent payload =
                objectMapper.convertValue(event.getPayload(), CartUpdatedEvent.class);

        cartAnalyticsService.recordCartUpdated(payload, event.getOccurredAt());
    }
}
