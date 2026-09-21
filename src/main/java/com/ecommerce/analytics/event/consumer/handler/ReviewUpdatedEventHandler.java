package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.review.ReviewUpdatedEvent;
import com.ecommerce.analytics.service.ReviewAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ReviewUpdatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ReviewAnalyticsService reviewAnalyticsService;

    public ReviewUpdatedEventHandler(
            ObjectMapper objectMapper,
            ReviewAnalyticsService reviewAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.reviewAnalyticsService = reviewAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.REVIEW_UPDATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        ReviewUpdatedEvent payload =
                objectMapper.convertValue(event.getPayload(), ReviewUpdatedEvent.class);

        reviewAnalyticsService.recordReviewUpdated(payload, event.getOccurredAt());
    }
}
