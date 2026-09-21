package com.ecommerce.analytics.analytics.event.producer;

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

    /**
     * Inside a DB transaction the event is sent only AFTER the commit, so a
     * rolled-back request never produces an event. Outside a transaction it is
     * sent immediately.
     */
    public void publish(EventEnvelope<?> event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send(event);
                        }
                    }
            );
        } else {
            send(event);
        }
    }

    private void send(EventEnvelope<?> event) {
        String key = String.valueOf(event.getAggregateId());

        try {
            kafkaTemplate.send(KafkaTopics.DOMAIN_EVENTS, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish {} (event {})",
                                    event.getEventType(), event.getEventId(), ex);
                        }
                    });
        } catch (Exception ex) {
            // The DB commit already happened; don't fail the HTTP request.
            log.error("Failed to publish {} (event {})",
                    event.getEventType(), event.getEventId(), ex);
        }
    }
}
