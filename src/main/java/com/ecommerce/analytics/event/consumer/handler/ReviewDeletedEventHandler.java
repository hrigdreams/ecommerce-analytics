package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.review.ReviewDeletedEvent;
import com.ecommerce.analytics.service.ReviewAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ReviewDeletedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ReviewAnalyticsService reviewAnalyticsService;

    public ReviewDeletedEventHandler(
            ObjectMapper objectMapper,
            ReviewAnalyticsService reviewAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.reviewAnalyticsService = reviewAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.REVIEW_DELETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        ReviewDeletedEvent payload =
                objectMapper.convertValue(event.getPayload(), ReviewDeletedEvent.class);

        reviewAnalyticsService.recordReviewDeleted(payload, event.getOccurredAt());
    }
}
