package com.ecommerce.analytics.event.producer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.config.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Kafka key = aggregate id. */
    public void publish(EventEnvelope<?> event) {
        publish(event, String.valueOf(event.getAggregateId()));
    }

    /**
     * Same as publish(event) but with an explicit Kafka key. Events with the same key
     * go to the same partition, so they are consumed in the order they were sent.
     * Inside a DB transaction the event is sent only AFTER the commit.
     */
    public void publish(EventEnvelope<?> event, String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send(event, key);
                        }
                    }
            );
        } else {
            send(event, key);
        }
    }

    /**
     * Sends immediately, ignoring any active transaction. Use only for events that
     * must be recorded regardless of whether the surrounding transaction commits —
     * e.g. CHECKOUT_STARTED, which should count as a funnel "start" even if the
     * order that follows fails validation and rolls back.
     */
    public void publishNow(EventEnvelope<?> event) {
        send(event, String.valueOf(event.getAggregateId()));
    }

    private void send(EventEnvelope<?> event, String key) {
        try {
            kafkaTemplate.send(KafkaTopics.DOMAIN_EVENTS, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} (event {})",
                                    event.getEventType(), event.getEventId(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish {} (event {})",
                    event.getEventType(), event.getEventId(), ex);
        }
    }
}
