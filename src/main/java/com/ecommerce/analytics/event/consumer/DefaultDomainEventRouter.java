package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultDomainEventRouter implements DomainEventRouter {

    private final Map<EventType, DomainEventHandler> handlers;

    public DefaultDomainEventRouter(List<DomainEventHandler> eventHandlers) {
        this.handlers = new EnumMap<>(EventType.class);

        for (DomainEventHandler handler : eventHandlers) {
            DomainEventHandler previous =
                    this.handlers.put(
                            handler.supportedEventType(),
                            handler
                    );

            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple handlers registered for event type: "
                                + handler.supportedEventType()
                );
            }
        }
    }

    @Override
    public void route(EventEnvelope<JsonNode> event) {
        if (event == null) {
            throw new IllegalArgumentException(
                    "Domain event cannot be null"
            );
        }

        EventType eventType = event.getEventType();

        if (eventType == null) {
            throw new IllegalArgumentException(
                    "Domain event type cannot be null"
            );
        }

        DomainEventHandler handler = handlers.get(eventType);

        if (handler == null) {
            throw new IllegalArgumentException(
                    "No handler registered for event type: " + eventType
            );
        }

        handler.handle(event.getPayload());
    }
}