package com.ecommerce.analytics.analytics.event.consumer;

import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.consumer.DomainEventRouter;
import com.ecommerce.analytics.event.consumer.handler.DomainEventHandler;
import com.ecommerce.analytics.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultDomainEventRouter implements DomainEventRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultDomainEventRouter.class);

    private final Map<EventType, DomainEventHandler> handlers;
    private final ProcessedEventRepository processedEventRepository;

    public DefaultDomainEventRouter(
            List<DomainEventHandler> eventHandlers,
            ProcessedEventRepository processedEventRepository
    ) {
        this.processedEventRepository = processedEventRepository;
        this.handlers = new EnumMap<>(EventType.class);

        for (DomainEventHandler handler : eventHandlers) {
            DomainEventHandler previous =
                    this.handlers.put(handler.supportedEventType(), handler);

            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple handlers registered for event type: "
                                + handler.supportedEventType()
                );
            }
        }
    }

    /**
     * One DB transaction covers: "mark event as processed" + the handler's writes.
     * If the handler throws, both roll back and the event can be retried safely.
     */
    @Override
    @Transactional
    public void route(EventEnvelope<JsonNode> event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event cannot be null");
        }
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("Domain event type cannot be null");
        }
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Domain event id cannot be null");
        }

        DomainEventHandler handler = handlers.get(event.getEventType());

        if (handler == null) {
            // Not an analytics event yet (e.g. USER_*, CATEGORY_*). Ignore, don't fail.
            log.debug("No handler for {}; skipping event {}",
                    event.getEventType(), event.getEventId());
            return;
        }

        if (processedEventRepository.markProcessed(event.getEventId().toString()) == 0) {
            log.info("Duplicate event {} ({}) skipped",
                    event.getEventId(), event.getEventType());
            return;
        }

        handler.handle(event);
        log.info("Handled {} for aggregate {} (event {})",
                event.getEventType(), event.getAggregateId(), event.getEventId());
    }
}
