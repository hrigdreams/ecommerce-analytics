package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartItemAddedEvent;
import com.ecommerce.analytics.service.CartAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CartItemAddedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CartAnalyticsService cartAnalyticsService;

    public CartItemAddedEventHandler(
            ObjectMapper objectMapper,
            CartAnalyticsService cartAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.cartAnalyticsService = cartAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CART_ITEM_ADDED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CartItemAddedEvent payload =
                objectMapper.convertValue(event.getPayload(), CartItemAddedEvent.class);

        cartAnalyticsService.recordItemAdded(payload, event.getOccurredAt());
    }
}
