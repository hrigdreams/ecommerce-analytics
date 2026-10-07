package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import com.ecommerce.analytics.entity.Category;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.EsProcessedEventRepository;
import com.ecommerce.analytics.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
/**
  ProductIndexingService handles converting product changes into Elasticsearch documents and
 writing those documents into the configured Elasticsearch index.
 **/
/**
 * All the actual idempotency / ordering / retry logic lives here, independent
 * of Kafka - ElasticsearchIndexingConsumer only parses events and calls
 * these methods, and the backfill job (ProductBackfillService) calls
 * upsert() directly for existing Postgres rows (optionally against an
 * explicit physical index - see the indexCoordinates-accepting overloads,
 * added for the products-v2 reindex scenario).
 *
 * Reliability strategy (hardened - see the 2026-xx-xx review):
 *
 *   - ORDERING OF OPERATIONS (critical fix): Kafka event -> processed-event
 *     EXISTENCE CHECK (read-only) -> Elasticsearch operation (with retry) ->
 *     processed-event PERSISTENCE -> return (listener then lets Kafka ack).
 *     The previous version of this class called markProcessed() FIRST, which
 *     was a real bug: if the Elasticsearch write then failed (even after
 *     retries), the event was already recorded as "processed", so Kafka's
 *     redelivery would see it as a duplicate and skip it forever - silently
 *     losing the update. Persistence of the processed-event marker now only
 *     happens AFTER the Elasticsearch write succeeds.
 *
 *   - Idempotency: deterministic document id (productId) means re-applying
 *     the same upsert is always safe. This is what makes "ES succeeded but
 *     the processed-event INSERT itself then failed" safe to redeliver:
 *     Kafka will redeliver (the exception propagates, nothing was acked),
 *     the existence check sees no marker, the ES write runs again (same
 *     document id, same data - harmless), and the marker write is retried.
 *
 *   - Ordering / stale AND equal-timestamp updates: an incoming event only
 *     overwrites a document if its occurredAt is strictly after the
 *     document's lastEventOccurredAt, OR equal AND its eventId wins a
 *     lexicographic tie-break against the document's lastEventId. This
 *     makes replaying the same set of events in any order converge to the
 *     same final state. EventEnvelope#version is NOT used for this -
 *     EventEnvelopeFactory hard-codes it to 1 on every event, verified by
 *     reading it directly, so it carries no ordering information.
 *
 *   - Kafka key / partition ordering (verified against the actual
 *     publishers): ProductService publishes PRODUCT_CREATED/UPDATED/DELETED
 *     with no explicit key, so DomainEventPublisher keys by aggregateId =
 *     productId - all events for one product land on the same partition and
 *     are delivered in order. ReviewService, however, keys review events by
 *     the REVIEW's own id (aggregateType "Review"), NOT productId - two
 *     reviews for the SAME product can land on different partitions with no
 *     ordering guarantee between them. recomputeReviewAggregate()
 *     tolerates this by always reading a FRESH aggregate from Postgres
 *     rather than applying a delta, which is self-correcting as long as
 *     processing stays single-threaded (verified: no `concurrency` is
 *     configured on this @KafkaListener, so it defaults to 1 - see the
 *     class doc on ElasticsearchIndexingConsumer and "remaining concerns"
 *     in the hardening report for what would need to change if that is ever
 *     increased or this consumer is horizontally scaled).
 *
 *   - Elasticsearch downtime / retry classification: retries are bounded (3
 *     attempts, linear backoff) and ONLY for transient failures - connection
 *     refused/timeout (DataAccessResourceFailureException, or a
 *     ConnectException/SocketTimeoutException/IOException found in the
 *     cause chain of an UncategorizedElasticsearchException, which is how
 *     Spring Data Elasticsearch generally wraps lower-level client/transport
 *     errors). Anything else - including a well-formed 4xx from Elasticsearch
 *     itself (bad request, mapping error) - is NOT retried; it is rethrown
 *     immediately, since retrying a permanently-invalid request cannot ever
 *     succeed and would just waste time before giving up anyway. This
 *     project has no spring-retry/resilience4j dependency, so this is a
 *     small manual retry loop rather than a new library for a few lines of
 *     logic.
 */
@Service
public class ProductIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexingService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final ElasticsearchOperations elasticsearchOperations;
    private final CategoryRepository categoryRepository;
    private final EsProcessedEventRepository esProcessedEventRepository;
    private final ReviewRepository reviewRepository;

    public ProductIndexingService(
            ElasticsearchOperations elasticsearchOperations,
            CategoryRepository categoryRepository,
            EsProcessedEventRepository esProcessedEventRepository,
            ReviewRepository reviewRepository
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.categoryRepository = categoryRepository;
        this.esProcessedEventRepository = esProcessedEventRepository;
        this.reviewRepository = reviewRepository;
    }

    /** Default coordinates: resolves to the "products" ALIAS (whatever physical index it currently points at). */
    private IndexCoordinates defaultIndex() {
        return elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);
    }

    /**
     * @return true if this event was newly processed, false if it was a
     * duplicate (already recorded by eventId) and nothing happened.
     */
    @Transactional
    public boolean upsertProduct(
            String eventId, Long productId, String name, Double price, Long categoryId, Instant occurredAt
    ) {
        return upsertProduct(eventId, productId, name, price, categoryId, occurredAt, defaultIndex());
    }

    /**
     * @param index explicit physical index to target (e.g. "products-v2" during a
     *              reindex), bypassing the alias - see ProductBackfillService.
     */
    public boolean upsertProduct(
            String eventId, Long productId, String name, Double price, Long categoryId, Instant occurredAt,
            IndexCoordinates index
    ) {
        if (alreadyProcessed(eventId)) {
            log.debug("[es-index] duplicate event {} for product {} - skipped", eventId, productId);
            return false;
        }

        ProductDocument doc = withRetry(() -> elasticsearchOperations.get(String.valueOf(productId), ProductDocument.class, index));

        if (isStale(doc, occurredAt, eventId)) {
            log.info("[es-index] stale/superseded event {} for product {} - skipped, not applied", eventId, productId);
            markProcessed(eventId); // recorded as seen so it is not retried forever; intentionally not applied
            return true;
        }

        if (doc == null) {
            doc = new ProductDocument();
            doc.setId(productId);
        }

        doc.setName(name);
        doc.setPrice(price);
        doc.setCategoryId(categoryId);
        doc.setCategoryName(resolveCategoryName(categoryId));
        doc.setLastEventOccurredAt(occurredAt);
        doc.setLastEventId(eventId);
        doc.setUpdatedAt(Instant.now());

        ProductDocument toSave = doc;
        withRetry(() -> elasticsearchOperations.save(toSave, index));

        // Only now - ES write has succeeded - record this event as processed.
        // If THIS insert fails, the exception propagates uncaught, Kafka does not
        // ack, and redelivery safely re-applies the (idempotent) upsert above.
        markProcessed(eventId);

        log.info("[es-index] upserted product {} (event {}) into {}", productId, eventId, index.getIndexName());
        return true;
    }

    public boolean deleteProduct(String eventId, Long productId, Instant occurredAt) {
        return deleteProduct(eventId, productId, occurredAt, defaultIndex());
    }

    public boolean deleteProduct(String eventId, Long productId, Instant occurredAt, IndexCoordinates index) {
        if (alreadyProcessed(eventId)) {
            log.debug("[es-index] duplicate delete event {} for product {} - skipped", eventId, productId);
            return false;
        }

        // A delete always wins regardless of lastEventOccurredAt ordering: there is
        // no "stale delete" case that should leave a deleted product searchable.
        withRetry(() -> {
            elasticsearchOperations.delete(String.valueOf(productId), index);
            return null;
        });

        markProcessed(eventId);
        log.info("[es-index] deleted product {} from {} (event {})", productId, index.getIndexName(), eventId);
        return true;
    }

    /**
     * Recomputes a product's averageRating/reviewCount straight from Postgres
     * (the source of truth) and writes the result to its ES document. Used
     * for REVIEW_CREATED, REVIEW_UPDATED, and REVIEW_DELETED alike - a fresh
     * recompute is simpler and strictly more correct than an incremental
     * delta here (see ReviewRepository#findAverageRatingByProductId for why
     * a delta can't actually be computed from REVIEW_UPDATED's payload
     * alone). See this class's top-level doc for the cross-review ordering
     * caveat.
     */
    public boolean recomputeReviewAggregate(String eventId, Long productId, Instant occurredAt) {
        if (alreadyProcessed(eventId)) {
            return false;
        }

        ProductDocument doc = withRetry(() -> elasticsearchOperations.get(String.valueOf(productId), ProductDocument.class, defaultIndex()));
        if (doc == null) {
            // The product itself hasn't been indexed yet (event arrived before PRODUCT_CREATED
            // was processed, or the product predates this feature and hasn't been backfilled).
            // Nothing to attach the rating to - do not fabricate a product document from a review event.
            log.warn("[es-index] review event {} for product {} arrived before the product was indexed - skipped", eventId, productId);
            markProcessed(eventId);
            return true;
        }

        Double average = reviewRepository.findAverageRatingByProductId(productId);
        long count = reviewRepository.countByProductId(productId);

        doc.setReviewCount((int) count);
        doc.setAverageRating(average == null ? null : Math.round(average * 100.0) / 100.0);
        doc.setLastEventOccurredAt(occurredAt);
        doc.setLastEventId(eventId);
        doc.setUpdatedAt(Instant.now());

        ProductDocument toSave = doc;
        withRetry(() -> elasticsearchOperations.save(toSave, defaultIndex()));

        markProcessed(eventId);
        return true;
    }

    // ------------------------------------------------------------------
    // Idempotency ledger (read-check vs. persist are now two separate
    // steps - see class doc for why this ordering matters)
    // ------------------------------------------------------------------

    private boolean alreadyProcessed(String eventId) {
        return esProcessedEventRepository.existsById(eventId);
    }

    private void markProcessed(String eventId) {
        esProcessedEventRepository.markProcessed(eventId);
    }

    // ------------------------------------------------------------------
    // Ordering
    // ------------------------------------------------------------------

    /** True if `doc` already reflects an event at least as new as (occurredAt, eventId). */
    private boolean isStale(ProductDocument doc, Instant occurredAt, String eventId) {
        if (doc == null || doc.getLastEventOccurredAt() == null) {
            return false;
        }
        int cmp = occurredAt.compareTo(doc.getLastEventOccurredAt());
        if (cmp != 0) {
            return cmp < 0;
        }
        // Equal timestamps: deterministic tie-break. Larger eventId (lexicographically)
        // always wins, regardless of processing order - see class/field docs.
        if (doc.getLastEventId() == null) {
            return false;
        }
        return eventId.compareTo(doc.getLastEventId()) < 0;
    }

    // ------------------------------------------------------------------
    // Category lookup
    // ------------------------------------------------------------------

    private String resolveCategoryName(Long categoryId) {
        if (categoryId == null) return null;
        Optional<Category> category = categoryRepository.findById(categoryId);
        return category.map(Category::getName).orElse(null);
    }

    // ------------------------------------------------------------------
    // Retry (transient failures only - see class doc)
    // ------------------------------------------------------------------

    private <T> T withRetry(Callable<T> action) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                if (!isTransient(e)) {
                    // Permanent failure (bad request, mapping error, programming bug, ...) -
                    // retrying cannot help. Fail fast rather than burn 3 attempts on
                    // something that will never succeed.
                    throw (e instanceof RuntimeException re) ? re : new IllegalStateException("Elasticsearch indexing failed", e);
                }
                lastError = (e instanceof RuntimeException re) ? re : new IllegalStateException("Elasticsearch indexing failed", e);
                log.warn("[es-index] transient Elasticsearch failure (attempt {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }
        // Exhausted retries: rethrow so the caller does NOT persist the processed-event
        // marker and the Kafka listener does NOT ack - the message is redelivered later.
        throw lastError;
    }

    /**
     * Connection-level failures (refused/timeout) are retried; everything else
     * (including a well-formed 4xx response from Elasticsearch itself) is not.
     * This is a best-effort classification based on Spring Data Elasticsearch's
     * documented exception categories (DataAccessResourceFailureException for
     * resource/connection problems) plus walking the cause chain for the
     * lower-level network exceptions Spring typically wraps. It has NOT been
     * verified against a live cluster's actual 5xx responses (that would need
     * a specific status-code check this project cannot exercise without a
     * running Elasticsearch - see "remaining concerns" in the hardening
     * report) - if real-world testing shows a genuine 5xx is NOT being
     * retried, extend this method rather than the retry loop itself.
     */
    private boolean isTransient(Throwable e) {
        if (e instanceof DataAccessResourceFailureException) {
            return true;
        }
        if (e instanceof UncategorizedElasticsearchException) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof ConnectException
                        || cause instanceof SocketTimeoutException
                        || cause instanceof IOException) {
                    return true;
                }
                cause = cause.getCause();
            }
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
