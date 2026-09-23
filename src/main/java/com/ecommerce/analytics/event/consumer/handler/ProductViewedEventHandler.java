package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.service.FunnelAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class ProductViewedEventHandler implements DomainEventHandler {

    private final FunnelAnalyticsService funnelAnalyticsService;

    public ProductViewedEventHandler(FunnelAnalyticsService funnelAnalyticsService) {
        this.funnelAnalyticsService = funnelAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PRODUCT_VIEWED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        // Payload carries productId/productName/userId, but only the funnel
        // count-by-day is built for now; per-product view counts can be added
        // to ProductAnalyticsService later using the same payload.
        funnelAnalyticsService.recordProductViewed(event.getOccurredAt());
    }
}
