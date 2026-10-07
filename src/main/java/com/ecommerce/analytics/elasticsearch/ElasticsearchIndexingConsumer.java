package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.product.ProductCreatedEvent;
import com.ecommerce.analytics.event.payload.product.ProductDeletedEvent;
import com.ecommerce.analytics.event.payload.product.ProductUpdatedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewCreatedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewDeletedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewUpdatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the SAME topic (app.kafka.topics.domain-events) as the existing
 * DomainEventConsumer, but as a completely independent Kafka consumer:
 * its own consumer group (app.kafka.consumer.elasticsearch-group-id,
 * default "elasticsearch-indexer", distinct from "analytics-consumer" and
 * Spark's "spark-streaming-analytics"), so Kafka gives it its own full copy
 * of every message and its own independently-tracked offsets.
 * <p>
 * This is deliberately NOT a DomainEventHandler registered with
 * DefaultDomainEventRouter: that router enforces exactly one handler per
 * EventType and throws IllegalStateException on a second registration for
 * the same type (verified in DefaultDomainEventRouter's constructor) -
 * PRODUCT_CREATED etc. already have a handler there. Routing here is a
 * small local switch instead, entirely separate from that router.
 * <p>
 * Only event types this phase's search index actually needs are handled;
 * every other EventType on the topic (USER_*, CART_*, ORDER_*, PAYMENT_*,
 * CATEGORY_*, ...) is intentionally ignored, not an error.
 * <p>
 * Acknowledgment: Spring Kafka's default AckMode (RECORD, auto-commit after
 * the listener method returns normally) means a message is only considered
 * processed if this method returns without throwing. Every path below either
 * completes successfully or lets an exception propagate (see
 * ProductIndexingService's retry/rethrow behavior) - nothing here catches
 * and swallows a real failure, so a failed message is redelivered and
 * retried rather than silently dropped.
 */
@Component
public class ElasticsearchIndexingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexingConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProductIndexingService productIndexingService;

    public ElasticsearchIndexingConsumer(ObjectMapper objectMapper, ProductIndexingService productIndexingService) {
        this.objectMapper = objectMapper;
        this.productIndexingService = productIndexingService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.domain-events:ecommerce.domain.events.v1}",
            groupId = "${app.kafka.consumer.elasticsearch-group-id:elasticsearch-indexer}"
    )
    public void consume(String message) {
        final EventEnvelope<JsonNode> event;

        try {
            event = objectMapper.readValue(
                    message,
                    objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, JsonNode.class)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("[es-index] Failed to deserialize domain event", exception);
        }

        if (event.getEventType() == null) {
            throw new IllegalArgumentException("[es-index] Domain event type cannot be null");
        }

        String eventId = String.valueOf(event.getEventId());

        switch (event.getEventType()) {
            case PRODUCT_CREATED -> {
                ProductCreatedEvent payload = objectMapper.convertValue(event.getPayload(), ProductCreatedEvent.class);
                productIndexingService.upsertProduct(
                        eventId, payload.getProductId(), payload.getName(), payload.getPrice(),
                        payload.getCategoryId(), event.getOccurredAt());
            }
            case PRODUCT_UPDATED -> {
                ProductUpdatedEvent payload = objectMapper.convertValue(event.getPayload(), ProductUpdatedEvent.class);
                productIndexingService.upsertProduct(
                        eventId, payload.getProductId(), payload.getName(), payload.getPrice(),
                        payload.getCategoryId(), event.getOccurredAt());
            }
            case PRODUCT_DELETED -> {
                ProductDeletedEvent payload = objectMapper.convertValue(event.getPayload(), ProductDeletedEvent.class);
                productIndexingService.deleteProduct(eventId, payload.getProductId(), event.getOccurredAt());
            }
            case REVIEW_CREATED -> {
                ReviewCreatedEvent payload = objectMapper.convertValue(event.getPayload(), ReviewCreatedEvent.class);
                productIndexingService.recomputeReviewAggregate(eventId, payload.getProductId(), event.getOccurredAt());
            }
            case REVIEW_UPDATED -> {
                ReviewUpdatedEvent payload = objectMapper.convertValue(event.getPayload(), ReviewUpdatedEvent.class);
                productIndexingService.recomputeReviewAggregate(eventId, payload.getProductId(), event.getOccurredAt());
            }
            case REVIEW_DELETED -> {
                ReviewDeletedEvent payload = objectMapper.convertValue(event.getPayload(), ReviewDeletedEvent.class);
                productIndexingService.recomputeReviewAggregate(eventId, payload.getProductId(), event.getOccurredAt());
            }
            default ->
                    log.debug("[es-index] ignoring event type {} (not relevant to the product search index)", event.getEventType());
        }
    }
}
