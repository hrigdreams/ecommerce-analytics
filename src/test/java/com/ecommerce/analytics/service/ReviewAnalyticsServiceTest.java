package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.ReviewAnalytics;
import com.ecommerce.analytics.event.payload.review.ReviewCreatedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewDeletedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewUpdatedEvent;
import com.ecommerce.analytics.repository.ReviewAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewAnalyticsServiceTest {

    private ReviewAnalyticsRepository repository;
    private ProductAnalyticsService productAnalyticsService;
    private ReviewAnalyticsService service;
    private final Instant at = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(ReviewAnalyticsRepository.class);
        productAnalyticsService = mock(ProductAnalyticsService.class);
        service = new ReviewAnalyticsService(repository, productAnalyticsService);
    }

    @Test
    void recordReviewCreated_shouldSaveRowAndUpdateProduct() {
        when(repository.existsById(1L)).thenReturn(false);

        ReviewCreatedEvent event = new ReviewCreatedEvent(1L, 7L, 10L, 5, "Great!", at);

        service.recordReviewCreated(event, at);

        verify(repository).save(argThat(r -> r.getReviewId().equals(1L) && r.getRating() == 5));
        verify(productAnalyticsService).addReview(10L, 5, at);
    }

    @Test
    void recordReviewCreated_shouldSkipWhenAlreadyRecorded() {
        when(repository.existsById(1L)).thenReturn(true);

        service.recordReviewCreated(new ReviewCreatedEvent(1L, 7L, 10L, 5, "Great!", at), at);

        verify(repository, never()).save(any());
        verify(productAnalyticsService, never()).addReview(any(), anyInt(), any());
    }

    @Test
    void recordReviewUpdated_shouldPropagateRatingChangeToProduct() {
        ReviewAnalytics row = new ReviewAnalytics();
        row.setReviewId(1L);
        row.setProductId(10L);
        row.setRating(3);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.recordReviewUpdated(new ReviewUpdatedEvent(1L, 7L, 10L, 5, "Updated"), at);

        assertEquals(5, row.getRating());
        verify(productAnalyticsService).updateReview(10L, 3, 5, at);
    }

    @Test
    void recordReviewUpdated_shouldNotCallProductWhenRatingUnchanged() {
        ReviewAnalytics row = new ReviewAnalytics();
        row.setReviewId(1L);
        row.setProductId(10L);
        row.setRating(4);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.recordReviewUpdated(new ReviewUpdatedEvent(1L, 7L, 10L, 4, "Same"), at);

        verify(productAnalyticsService, never()).updateReview(any(), anyInt(), anyInt(), any());
    }

    @Test
    void recordReviewUpdated_shouldSkipWhenReviewUnknown() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.recordReviewUpdated(new ReviewUpdatedEvent(1L, 7L, 10L, 4, "x"), at);

        verify(repository, never()).save(any());
    }

    @Test
    void recordReviewDeleted_shouldRemoveRowAndProduct() {
        ReviewAnalytics row = new ReviewAnalytics();
        row.setReviewId(1L);
        row.setProductId(10L);
        row.setRating(4);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.recordReviewDeleted(new ReviewDeletedEvent(1L, 7L, 10L), at);

        verify(productAnalyticsService).removeReview(10L, 4, at);
        verify(repository).delete(row);
    }

    @Test
    void recordReviewDeleted_shouldSkipWhenReviewUnknown() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.recordReviewDeleted(new ReviewDeletedEvent(1L, 7L, 10L), at);

        verify(repository, never()).delete(any());
    }

    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }

    private static ReviewAnalytics argThat(java.util.function.Predicate<ReviewAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
