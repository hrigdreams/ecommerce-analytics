package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.payment.PaymentCreatedEvent;
import com.ecommerce.analytics.service.OrderAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PaymentCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final OrderAnalyticsService orderAnalyticsService;

    public PaymentCreatedEventHandler(
            ObjectMapper objectMapper,
            OrderAnalyticsService orderAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.orderAnalyticsService = orderAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.PAYMENT_CREATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        PaymentCreatedEvent payload =
                objectMapper.convertValue(event.getPayload(), PaymentCreatedEvent.class);

        orderAnalyticsService.recordPayment(payload.getOrderId(), payload.getStatus(), event.getOccurredAt());
    }
}
