package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.cart.CartCreatedEvent;
import com.ecommerce.analytics.service.CartAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CartCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CartAnalyticsService cartAnalyticsService;

    public CartCreatedEventHandler(
            ObjectMapper objectMapper,
            CartAnalyticsService cartAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.cartAnalyticsService = cartAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CART_CREATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CartCreatedEvent payload =
                objectMapper.convertValue(event.getPayload(), CartCreatedEvent.class);

        cartAnalyticsService.recordCartCreated(payload, event.getOccurredAt());
    }
}
