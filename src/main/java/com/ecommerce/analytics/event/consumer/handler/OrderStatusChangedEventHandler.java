package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.order.OrderStatusChangedEvent;
import com.ecommerce.analytics.service.OrderAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusChangedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final OrderAnalyticsService orderAnalyticsService;

    public OrderStatusChangedEventHandler(
            ObjectMapper objectMapper,
            OrderAnalyticsService orderAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.orderAnalyticsService = orderAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.ORDER_STATUS_CHANGED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        OrderStatusChangedEvent payload =
                objectMapper.convertValue(event.getPayload(), OrderStatusChangedEvent.class);

        orderAnalyticsService.recordStatusChange(payload.getOrderId(), payload.getNewStatus(), event.getOccurredAt());
    }
}
