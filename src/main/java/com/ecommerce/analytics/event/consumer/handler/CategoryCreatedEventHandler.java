package com.ecommerce.analytics.event.consumer.handler;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.category.CategoryCreatedEvent;
import com.ecommerce.analytics.service.CategoryAnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class CategoryCreatedEventHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final CategoryAnalyticsService categoryAnalyticsService;

    public CategoryCreatedEventHandler(
            ObjectMapper objectMapper,
            CategoryAnalyticsService categoryAnalyticsService
    ) {
        this.objectMapper = objectMapper;
        this.categoryAnalyticsService = categoryAnalyticsService;
    }

    @Override
    public EventType supportedEventType() {
        return EventType.CATEGORY_CREATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> event) {
        CategoryCreatedEvent payload =
                objectMapper.convertValue(event.getPayload(), CategoryCreatedEvent.class);

        categoryAnalyticsService.upsertCategory(payload.getCategoryId(), payload.getName(), event.getOccurredAt());
    }
}
