package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;

public interface DomainEventRouter {

    void route(EventEnvelope<JsonNode> event);
}