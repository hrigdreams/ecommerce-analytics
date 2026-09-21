package com.ecommerce.analytics.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.ecommerce.analytics.event.payload.product.ProductDeletedEvent;
import com.ecommerce.analytics.service.ProductAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ProductDeletedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ProductAnalyticsService productAnalyticsService;

    public ProductDeletedEventHandler(
            ObjectMapper objectMapper,
            ProductAnalyticsService productAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.productAnalyticsService = productAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PRODUCT_DELETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        ProductDeletedEvent payload =
                objectMapper.convertValue(event.getPayload(), ProductDeletedEvent.class);

        productAnalyticsService.markDeleted(payload.getProductId(), event.getOccurredAt());
    }
}
