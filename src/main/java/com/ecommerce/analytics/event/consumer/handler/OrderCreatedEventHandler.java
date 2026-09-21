package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.order.OrderCreatedEvent;
import com.ecommerce.analytics.service.OrderAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final OrderAnalyticsService orderAnalyticsService;

    public OrderCreatedEventHandler(
            ObjectMapper objectMapper,
            OrderAnalyticsService orderAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.orderAnalyticsService = orderAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.ORDER_CREATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        OrderCreatedEvent payload =
                objectMapper.convertValue(event.getPayload(), OrderCreatedEvent.class);

        orderAnalyticsService.recordOrderCreated(payload, event.getOccurredAt());
    }
}
