package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.service.OrderAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderAndPaymentEventHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OrderAnalyticsService service = mock(OrderAnalyticsService.class);
    private final Instant occurredAt = Instant.parse("2026-09-21T10:00:00Z");

    private EventEnvelope<JsonNode> envelope(EventType type, JsonNode payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, occurredAt, "Order", 1L, 1, payload);
    }

    @Test
    void orderCreatedHandler_shouldRecordOrder() {
        OrderCreatedEventHandler handler = new OrderCreatedEventHandler(objectMapper, service);
        assertEquals(EventType.ORDER_CREATED, handler.supportedEventType());

        var payload = objectMapper.createObjectNode()
                .put("orderId", 1L)
                .put("userId", 7L)
                .put("status", "PENDING")
                .put("paymentStatus", "UNPAID")
                .put("totalAmount", 999.98);
        payload.putArray("items").addObject()
                .put("orderItemId", 1L)
                .put("productId", 10L)
                .put("productName", "Phone")
                .put("quantity", 2)
                .put("unitPrice", 499.99);

        handler.handle(envelope(EventType.ORDER_CREATED, payload));

        verify(service).recordOrderCreated(
                org.mockito.ArgumentMatchers.argThat(e ->
                        e.getOrderId() == 1L && e.getItems().size() == 1),
                org.mockito.ArgumentMatchers.eq(occurredAt)
        );
    }

    @Test
    void statusChangedHandler_shouldRecordNewStatus() {
        OrderStatusChangedEventHandler handler =
                new OrderStatusChangedEventHandler(objectMapper, service);
        assertEquals(EventType.ORDER_STATUS_CHANGED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode()
                .put("orderId", 1L)
                .put("previousStatus", "PENDING")
                .put("newStatus", "SHIPPED");

        handler.handle(envelope(EventType.ORDER_STATUS_CHANGED, payload));

        verify(service).recordStatusChange(1L, "SHIPPED", occurredAt);
    }

    @Test
    void paymentCreatedHandler_shouldRecordPayment() {
        PaymentCreatedEventHandler handler =
                new PaymentCreatedEventHandler(objectMapper, service);
        assertEquals(EventType.PAYMENT_CREATED, handler.supportedEventType());

        JsonNode payload = objectMapper.createObjectNode()
                .put("paymentId", 5L)
                .put("orderId", 1L)
                .put("paymentMethod", "CARD")
                .put("status", "PAID")
                .put("amount", 999.98)
                .put("transactionId", "txn-1");

        handler.handle(envelope(EventType.PAYMENT_CREATED, payload));

        verify(service).recordPayment(1L, "PAID", occurredAt);
    }
}
