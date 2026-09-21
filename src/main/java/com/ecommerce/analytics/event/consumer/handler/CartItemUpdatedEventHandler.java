package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartItemUpdatedEvent;
import com.ecommerce.analytics.service.CartAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CartItemUpdatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CartAnalyticsService cartAnalyticsService;

    public CartItemUpdatedEventHandler(
            ObjectMapper objectMapper,
            CartAnalyticsService cartAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.cartAnalyticsService = cartAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CART_ITEM_UPDATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CartItemUpdatedEvent payload =
                objectMapper.convertValue(event.getPayload(), CartItemUpdatedEvent.class);

        cartAnalyticsService.recordItemUpdated(payload, event.getOccurredAt());
    }
}
