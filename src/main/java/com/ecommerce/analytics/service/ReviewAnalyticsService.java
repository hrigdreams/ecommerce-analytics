package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.ReviewAnalytics;
import com.ecommerce.analytics.event.payload.review.ReviewCreatedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewDeletedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewUpdatedEvent;
import com.ecommerce.analytics.repository.ReviewAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReviewAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(ReviewAnalyticsService.class);

    private final ReviewAnalyticsRepository repository;
    private final ProductAnalyticsService productAnalyticsService;

    public ReviewAnalyticsService(
            ReviewAnalyticsRepository repository,
            ProductAnalyticsService productAnalyticsService
    ) {
        this.repository = repository;
        this.productAnalyticsService = productAnalyticsService;
    }

    /** REVIEW_CREATED: maintains review_count / rating_sum / average_rating on the product. */
    @Transactional
    public void recordReviewCreated(ReviewCreatedEvent event, Instant occurredAt) {
        if (repository.existsById(event.getReviewId())) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        int rating = event.getRating() != null ? event.getRating() : 0;

        ReviewAnalytics row = new ReviewAnalytics();
        row.setReviewId(event.getReviewId());
        row.setUserId(event.getUserId());
        row.setProductId(event.getProductId());
        row.setRating(rating);
        row.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt() : at);
        row.setUpdatedAt(at);
        repository.save(row);

        productAnalyticsService.addReview(event.getProductId(), rating, at);
    }

    /** REVIEW_UPDATED. */
    @Transactional
    public void recordReviewUpdated(ReviewUpdatedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        ReviewAnalytics row = repository.findById(event.getReviewId()).orElse(null);
        if (row == null) {
            log.warn("Review {} not in analytics; update skipped", event.getReviewId());
            return;
        }

        int oldRating = row.getRating();
        int newRating = event.getRating() != null ? event.getRating() : oldRating;

        row.setRating(newRating);
        row.setUpdatedAt(at);
        repository.save(row);

        if (newRating != oldRating) {
            productAnalyticsService.updateReview(row.getProductId(), oldRating, newRating, at);
        }
    }

    /** REVIEW_DELETED. */
    @Transactional
    public void recordReviewDeleted(ReviewDeletedEvent event, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        ReviewAnalytics row = repository.findById(event.getReviewId()).orElse(null);
        if (row == null) {
            log.warn("Review {} not in analytics; delete skipped", event.getReviewId());
            return;
        }

        productAnalyticsService.removeReview(row.getProductId(), row.getRating(), at);
        repository.delete(row);
    }
}
