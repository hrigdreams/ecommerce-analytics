package com.ecommerce.analytics.event;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EventEnvelopeFactory {

    public <T> EventEnvelope<T> create(
            EventType eventType,
            String aggregateType,
            Long aggregateId,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                aggregateType,
                aggregateId,
                1,
                payload
        );
    }
}