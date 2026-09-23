package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.service.FunnelAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class CheckoutStartedEventHandler implements DomainEventHandler {

    private final FunnelAnalyticsService funnelAnalyticsService;

    public CheckoutStartedEventHandler(FunnelAnalyticsService funnelAnalyticsService) {
        this.funnelAnalyticsService = funnelAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CHECKOUT_STARTED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        funnelAnalyticsService.recordCheckoutStarted(event.getOccurredAt());
    }
}
