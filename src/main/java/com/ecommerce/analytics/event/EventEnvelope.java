package com.ecommerce.analytics.event;

import java.time.Instant;
import java.util.UUID;

public class EventEnvelope<T> {

    private UUID eventId;
    private EventType eventType;
    private Instant occurredAt;

    private String aggregateType;
    private Long aggregateId;

    private int version;

    private T payload;

    public EventEnvelope() {
    }

    public EventEnvelope(
            UUID eventId,
            EventType eventType,
            Instant occurredAt,
            String aggregateType,
            Long aggregateId,
            int version,
            T payload
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.version = version;
        this.payload = payload;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(Long aggregateId) {
        this.aggregateId = aggregateId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }
}