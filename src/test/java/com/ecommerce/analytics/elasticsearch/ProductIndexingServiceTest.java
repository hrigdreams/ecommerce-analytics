package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import com.ecommerce.analytics.repository.CategoryRepository;
import com.ecommerce.analytics.repository.EsProcessedEventRepository;
import com.ecommerce.analytics.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.net.ConnectException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductIndexingServiceTest {

    private ElasticsearchOperations elasticsearchOperations;
    private CategoryRepository categoryRepository;
    private EsProcessedEventRepository esProcessedEventRepository;
    private ReviewRepository reviewRepository;
    private ProductIndexingService service;

    private final Instant at = Instant.parse("2026-09-24T10:00:00Z");
    private final IndexCoordinates aliasIndex = IndexCoordinates.of("products");

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        categoryRepository = mock(CategoryRepository.class);
        esProcessedEventRepository = mock(EsProcessedEventRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        when(elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(aliasIndex);
        service = new ProductIndexingService(elasticsearchOperations, categoryRepository, esProcessedEventRepository, reviewRepository);
    }

    // ------------------------------------------------------------------
    // Ordering of operations (the critical fix): ES must succeed BEFORE
    // the processed-event marker is persisted.
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldOnlyMarkProcessedAfterElasticsearchWriteSucceeds() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertTrue(result);
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
        verify(esProcessedEventRepository).markProcessed("e1");
        // Save must happen before the marker is persisted, not the old (buggy) order.
        var inOrder = org.mockito.Mockito.inOrder(elasticsearchOperations, esProcessedEventRepository);
        inOrder.verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
        inOrder.verify(esProcessedEventRepository).markProcessed("e1");
    }

    @Test
    void upsertProduct_whenElasticsearchFailsPermanently_shouldNeverPersistTheProcessedMarker() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new IllegalArgumentException("malformed field"));

        assertThrows(RuntimeException.class, () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        // This is the exact bug being fixed: the marker must NEVER be written
        // when Elasticsearch did not actually succeed, or a redelivered event
        // would be silently skipped as "already processed" and never indexed.
        verify(esProcessedEventRepository, never()).markProcessed(anyString());
    }

    @Test
    void upsertProduct_whenProcessedMarkerPersistenceFails_shouldPropagate_redeliveryStaysSafeViaDeterministicId() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("db down"))
                .when(esProcessedEventRepository).markProcessed("e1");

        // ES write succeeded, but recording that fact failed - this must NOT be
        // swallowed (so Kafka does not ack); redelivery will re-run the upsert,
        // which is safe because the document id is deterministic (same productId).
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldSkipDuplicateEvent() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, 1L, at);

        assertFalse(result);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
        verify(elasticsearchOperations, never()).get(anyString(), eq(ProductDocument.class), any());
    }

    // ------------------------------------------------------------------
    // Ordering / staleness, including the equal-timestamp tie-break
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldRejectEventStrictlyOlderThanCurrentDocument() {
        when(esProcessedEventRepository.existsById("e-late")).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        existing.setLastEventOccurredAt(Instant.parse("2026-09-24T12:00:00Z"));
        existing.setLastEventId("e-current");
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);

        boolean result = service.upsertProduct("e-late", 10L, "Phone", 499.99, 1L, at);

        assertTrue(result); // recorded as seen...
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class)); // ...but not applied
        verify(esProcessedEventRepository).markProcessed("e-late"); // still marked, so it is not retried forever
    }

    @Test
    void upsertProduct_withEqualTimestamp_shouldApplyOnlyWhenEventIdWinsTheTieBreak() {
        when(esProcessedEventRepository.existsById(anyString())).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        existing.setLastEventOccurredAt(at); // identical timestamp
        existing.setLastEventId("m");
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);

        // "a" < "m" lexicographically -> loses the tie-break -> not applied.
        boolean losesTie = service.upsertProduct("a", 10L, "Phone", 1.0, null, at);
        assertTrue(losesTie);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));

        // "z" > "m" lexicographically -> wins the tie-break -> applied.
        boolean winsTie = service.upsertProduct("z", 10L, "Phone", 1.0, null, at);
        assertTrue(winsTie);
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
    }

    @Test
    void upsertProduct_sameEventIdReplayed_shouldBeIdempotentNotRejectedAsStale() {
        // Replaying the EXACT same event (same eventId, same occurredAt) against the
        // document IT ITSELF already produced must not be treated as a tie-break loss -
        // but this case never reaches isStale() at all in practice, because the
        // processed-event marker from the first delivery already short-circuits it.
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertFalse(result); // short-circuited as a duplicate, exactly as intended
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Test
    void deleteProduct_shouldRemoveFromIndexThenMarkProcessed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);

        boolean result = service.deleteProduct("e1", 10L, at);

        assertTrue(result);
        var inOrder = org.mockito.Mockito.inOrder(elasticsearchOperations, esProcessedEventRepository);
        inOrder.verify(elasticsearchOperations).delete("10", aliasIndex);
        inOrder.verify(esProcessedEventRepository).markProcessed("e1");
    }

    @Test
    void deleteProduct_shouldSkipDuplicateEvent() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(true);

        boolean result = service.deleteProduct("e1", 10L, at);

        assertFalse(result);
        verify(elasticsearchOperations, never()).delete(anyString(), any(IndexCoordinates.class));
    }

    // ------------------------------------------------------------------
    // Review aggregate recompute
    // ------------------------------------------------------------------

    @Test
    void recomputeReviewAggregate_shouldWriteFreshAverageAndCountFromPostgres() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);

        ProductDocument existing = new ProductDocument();
        existing.setId(10L);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(existing);
        when(reviewRepository.findAverageRatingByProductId(10L)).thenReturn(4.3333);
        when(reviewRepository.countByProductId(10L)).thenReturn(3L);

        boolean result = service.recomputeReviewAggregate("e1", 10L, at);

        assertTrue(result);
        verify(elasticsearchOperations).save(org.mockito.ArgumentMatchers.argThat((ProductDocument doc) ->
                doc.getReviewCount() == 3 && doc.getAverageRating() == 4.33), eq(aliasIndex));
    }

    @Test
    void recomputeReviewAggregate_shouldSkipWhenProductNotYetIndexed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex))).thenReturn(null);

        boolean result = service.recomputeReviewAggregate("e1", 10L, at);

        assertTrue(result);
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
        verify(esProcessedEventRepository).markProcessed("e1");
    }

    // ------------------------------------------------------------------
    // Retry classification: transient (connection/timeout) vs permanent
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_shouldRetryOnConnectionFailureThenSucceed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"))
                .thenReturn(null);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertTrue(result);
        verify(elasticsearchOperations, times(2)).get(eq("10"), eq(ProductDocument.class), eq(aliasIndex));
        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(aliasIndex));
    }

    @Test
    void upsertProduct_shouldRetryOnWrappedConnectExceptionThenSucceed() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        UncategorizedElasticsearchException wrapped =
                new UncategorizedElasticsearchException("transport error", new ConnectException("refused"));
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(wrapped)
                .thenReturn(null);

        boolean result = service.upsertProduct("e1", 10L, "Phone", 499.99, null, at);

        assertTrue(result);
        verify(elasticsearchOperations, times(2)).get(eq("10"), eq(ProductDocument.class), eq(aliasIndex));
    }

    @Test
    void upsertProduct_shouldExhaustRetriesAndRethrowOnPersistentConnectionFailure() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new DataAccessResourceFailureException("still down"));

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        verify(elasticsearchOperations, times(3)).get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)); // MAX_ATTEMPTS
        verify(esProcessedEventRepository, never()).markProcessed(anyString());
    }

    @Test
    void upsertProduct_shouldNotRetryAPermanentFailure_failsFastOnFirstAttempt() {
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(aliasIndex)))
                .thenThrow(new IllegalArgumentException("permanently invalid request"));

        assertThrows(RuntimeException.class,
                () -> service.upsertProduct("e1", 10L, "Phone", 499.99, null, at));

        // Exactly ONE attempt - retrying a permanent/4xx-style failure would just waste time.
        verify(elasticsearchOperations, times(1)).get(eq("10"), eq(ProductDocument.class), eq(aliasIndex));
    }

    // ------------------------------------------------------------------
    // Explicit physical index targeting (for the products-v2 reindex scenario)
    // ------------------------------------------------------------------

    @Test
    void upsertProduct_withExplicitIndexCoordinates_shouldTargetThatIndexNotTheAlias() {
        IndexCoordinates v2 = IndexCoordinates.of("products-v2");
        when(esProcessedEventRepository.existsById("e1")).thenReturn(false);
        when(elasticsearchOperations.get(eq("10"), eq(ProductDocument.class), eq(v2))).thenReturn(null);

        service.upsertProduct("e1", 10L, "Phone", 499.99, null, at, v2);

        verify(elasticsearchOperations).save(any(ProductDocument.class), eq(v2));
        verify(elasticsearchOperations, never()).save(any(ProductDocument.class), eq(aliasIndex));
    }
}
