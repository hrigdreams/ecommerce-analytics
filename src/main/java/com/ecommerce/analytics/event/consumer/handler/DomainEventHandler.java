package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.fasterxml.jackson.databind.JsonNode;

public interface DomainEventHandler {

    EventType supportedEventType();

    /** Receives the whole envelope so handlers can use eventId / occurredAt. */
    void handle(EventEnvelope<JsonNode> event);
}
