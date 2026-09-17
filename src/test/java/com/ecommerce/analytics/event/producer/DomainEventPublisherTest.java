package com.ecommerce.analytics.event.producer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

class DomainEventPublisherTest {

    @Test
    void shouldPublishEventUsingAggregateIdAsKafkaKey() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        DomainEventPublisher publisher =
                new DomainEventPublisher(kafkaTemplate);

        EventEnvelope<String> event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventType.USER_CREATED,
                Instant.now(),
                "User",
                42L,
                1,
                "test-payload"
        );

        publisher.publish(event);

        verify(kafkaTemplate).send(
                "ecommerce.domain.events.v1",
                "42",
                event
        );
    }
}