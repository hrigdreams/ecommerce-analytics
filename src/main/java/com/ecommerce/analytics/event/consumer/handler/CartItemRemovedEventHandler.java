package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartItemRemovedEvent;
import com.ecommerce.analytics.service.CartAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CartItemRemovedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CartAnalyticsService cartAnalyticsService;

    public CartItemRemovedEventHandler(
            ObjectMapper objectMapper,
            CartAnalyticsService cartAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.cartAnalyticsService = cartAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CART_ITEM_REMOVED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CartItemRemovedEvent payload =
                objectMapper.convertValue(event.getPayload(), CartItemRemovedEvent.class);

        cartAnalyticsService.recordItemRemoved(payload, event.getOccurredAt());
    }
}
