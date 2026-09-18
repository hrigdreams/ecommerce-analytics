package com.ecommerce.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DomainEventConsumer {

    private final ObjectMapper objectMapper;
    private final DomainEventRouter domainEventRouter;

    public DomainEventConsumer(
            ObjectMapper objectMapper,
            DomainEventRouter domainEventRouter
    ) {
        this.objectMapper = objectMapper;
        this.domainEventRouter = domainEventRouter;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.domain-events:ecommerce.domain.events.v1}",
            groupId = "${app.kafka.consumer.group-id:analytics-consumer}"
    )
    public void consume(String message) {
        final EventEnvelope<JsonNode> event;

        try {
            event = objectMapper.readValue(
                    message,
                    objectMapper.getTypeFactory()
                            .constructParametricType(
                                    EventEnvelope.class,
                                    JsonNode.class
                            )
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to deserialize domain event",
                    exception
            );
        }

        domainEventRouter.route(event);
    }
}