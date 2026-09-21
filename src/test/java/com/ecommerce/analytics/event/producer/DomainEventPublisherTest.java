package com.ecommerce.analytics.event.producer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainEventPublisherTest {

    private static final String TOPIC = "ecommerce.domain.events.v1";

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<>());
        return template;
    }

    private EventEnvelope<String> event() {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                EventType.USER_CREATED,
                Instant.now(),
                "User",
                42L,
                1,
                "test-payload"
        );
    }

    @Test
    void shouldPublishImmediatelyWhenNoTransactionIsActive() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplate();
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate);
        EventEnvelope<String> event = event();

        publisher.publish(event);

        verify(kafkaTemplate).send(TOPIC, "42", event);
    }

    @Test
    void shouldPublishOnlyAfterCommitInsideTransaction() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplate();
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate);
        EventEnvelope<String> event = event();

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(event);

            // Nothing sent before the commit happens.
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(kafkaTemplate).send(TOPIC, "42", event);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldNotPublishWhenTransactionRollsBack() {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplate();
        DomainEventPublisher publisher = new DomainEventPublisher(kafkaTemplate);

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish(event());

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );

            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
