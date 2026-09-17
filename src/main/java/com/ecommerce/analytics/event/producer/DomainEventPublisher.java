package com.ecommerce.analytics.event.producer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<?> publish(EventEnvelope<?> event) {
        String key = String.valueOf(event.getAggregateId());

        return kafkaTemplate.send(
                KafkaTopics.DOMAIN_EVENTS,
                key,
                event
        );
    }
}