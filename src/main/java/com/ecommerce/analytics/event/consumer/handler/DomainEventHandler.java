package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventType;
import com.fasterxml.jackson.databind.JsonNode;

public interface DomainEventHandler {

    EventType supportedEventType();

    void handle(JsonNode payload);
}