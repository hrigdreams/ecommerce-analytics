package com.ecommerce.analytics.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.ecommerce.analytics.event.payload.product.ProductCreatedEvent;
import com.ecommerce.analytics.service.ProductAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ProductAnalyticsService productAnalyticsService;

    public ProductCreatedEventHandler(
            ObjectMapper objectMapper,
            ProductAnalyticsService productAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.productAnalyticsService = productAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PRODUCT_CREATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        ProductCreatedEvent payload =
                objectMapper.convertValue(event.getPayload(), ProductCreatedEvent.class);

        productAnalyticsService.upsertProduct(
                payload.getProductId(),
                payload.getName(),
                payload.getCategoryId(),
                payload.getPrice(),
                event.getOccurredAt()
        );
    }
}
