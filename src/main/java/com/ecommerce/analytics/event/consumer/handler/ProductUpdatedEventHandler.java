package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.product.ProductUpdatedEvent;
import com.ecommerce.analytics.service.ProductAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductUpdatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ProductAnalyticsService productAnalyticsService;

    public ProductUpdatedEventHandler(
            ObjectMapper objectMapper,
            ProductAnalyticsService productAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.productAnalyticsService = productAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PRODUCT_UPDATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        ProductUpdatedEvent payload =
                objectMapper.convertValue(event.getPayload(), ProductUpdatedEvent.class);

        productAnalyticsService.upsertProduct(
                payload.getProductId(),
                payload.getName(),
                payload.getCategoryId(),
                payload.getPrice(),
                event.getOccurredAt()
        );
    }
}
