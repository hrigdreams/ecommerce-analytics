package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.product.ProductCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;

    public ProductCreatedEventHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PRODUCT_CREATED;
    }

    @Override
    public void handle(JsonNode payload) {
        ProductCreatedEvent event =
                objectMapper.convertValue(payload, ProductCreatedEvent.class);

        // Analytics/read-model processing will be added next.
    }
}