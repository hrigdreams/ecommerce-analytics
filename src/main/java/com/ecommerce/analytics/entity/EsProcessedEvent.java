package com.ecommerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Dedupe ledger for the Elasticsearch indexing consumer ONLY.
 *
 * This is deliberately a SEPARATE table from processed_events (which belongs
 * to the main Spring Kafka consumer, group "analytics-consumer"). Reusing
 * processed_events here would be a real bug: event_id is the same for a
 * given event regardless of which consumer group reads it, so if both
 * consumers called processed_events.markProcessed(eventId), whichever
 * consumer's transaction commits first would "claim" the event and the
 * other would see it as already-processed and silently skip it forever.
 * Each independent consumer group needs its own dedup bookkeeping, exactly
 * as each gets its own full copy of the Kafka topic.
 */
@Entity
@Table(name = "es_processed_events")
public class EsProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected EsProcessedEvent() {
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
