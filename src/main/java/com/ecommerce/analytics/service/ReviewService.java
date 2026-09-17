package com.ecommerce.analytics.service;

import com.ecommerce.analytics.dto.ReviewRequest;
import com.ecommerce.analytics.dto.ReviewResponse;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.Review;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.event.EventEnvelope;
import com.ecommerce.analytics.event.EventEnvelopeFactory;
import com.ecommerce.analytics.event.EventType;
import com.ecommerce.analytics.event.payload.review.ReviewCreatedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewDeletedEvent;
import com.ecommerce.analytics.event.payload.review.ReviewUpdatedEvent;
import com.ecommerce.analytics.event.producer.DomainEventPublisher;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.ReviewNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.ReviewRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EventEnvelopeFactory eventEnvelopeFactory;
    private final DomainEventPublisher domainEventPublisher;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            EventEnvelopeFactory eventEnvelopeFactory,
            DomainEventPublisher domainEventPublisher
    ) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.eventEnvelopeFactory = eventEnvelopeFactory;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public ReviewResponse createReview(ReviewRequest request) {

        User user = userRepository.findById(
                request.getUserId()
        ).orElseThrow(() ->
                new UserNotFoundException(
                        String.valueOf(request.getUserId())
                )
        );

        Product product = productRepository.findById(
                request.getProductId()
        ).orElseThrow(() ->
                new ProductNotFoundException(
                        String.valueOf(request.getProductId())
                )
        );

        Review review = new Review(
                user,
                product,
                request.getRating(),
                request.getComment()
        );

        Review saved = reviewRepository.save(review);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            java.time.Instant createdAt = null;

            if (saved.getCreatedAt() != null) {
                createdAt = saved.getCreatedAt()
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            }

            ReviewCreatedEvent payload = new ReviewCreatedEvent(
                    saved.getId(),
                    saved.getUser() != null
                            ? saved.getUser().getId()
                            : null,
                    saved.getProduct() != null
                            ? saved.getProduct().getId()
                            : null,
                    saved.getRating(),
                    saved.getComment(),
                    createdAt
            );

            EventEnvelope<ReviewCreatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.REVIEW_CREATED,
                            "Review",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public ReviewResponse updateReview(
            Long id,
            ReviewRequest request
    ) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() ->
                        new ReviewNotFoundException(
                                String.valueOf(id)
                        )
                );

        User user = userRepository.findById(
                request.getUserId()
        ).orElseThrow(() ->
                new UserNotFoundException(
                        String.valueOf(request.getUserId())
                )
        );

        Product product = productRepository.findById(
                request.getProductId()
        ).orElseThrow(() ->
                new ProductNotFoundException(
                        String.valueOf(request.getProductId())
                )
        );

        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review saved = reviewRepository.save(review);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            ReviewUpdatedEvent payload = new ReviewUpdatedEvent(
                    saved.getId(),
                    saved.getUser() != null
                            ? saved.getUser().getId()
                            : null,
                    saved.getProduct() != null
                            ? saved.getProduct().getId()
                            : null,
                    saved.getRating(),
                    saved.getComment()
            );

            EventEnvelope<ReviewUpdatedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.REVIEW_UPDATED,
                            "Review",
                            saved.getId(),
                            payload
                    );

            domainEventPublisher.publish(event);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public void deleteReview(Long id) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() ->
                        new ReviewNotFoundException(
                                String.valueOf(id)
                        )
                );

        Long userId = null;
        Long productId = null;

        if (review.getUser() != null) {
            userId = review.getUser().getId();
        }

        if (review.getProduct() != null) {
            productId = review.getProduct().getId();
        }

        reviewRepository.delete(review);

        if (eventEnvelopeFactory != null && domainEventPublisher != null) {

            ReviewDeletedEvent payload =
                    new ReviewDeletedEvent(
                            id,
                            userId,
                            productId
                    );

            EventEnvelope<ReviewDeletedEvent> event =
                    eventEnvelopeFactory.create(
                            EventType.REVIEW_DELETED,
                            "Review",
                            id,
                            payload
                    );

            domainEventPublisher.publish(event);
        }
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(Long id) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() ->
                        new ReviewNotFoundException(
                                String.valueOf(id)
                        )
                );

        return mapToResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getAllReviews() {

        return reviewRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ReviewResponse mapToResponse(Review review) {

        ReviewResponse response = new ReviewResponse();

        response.setId(review.getId());

        response.setUserId(
                review.getUser() != null
                        ? review.getUser().getId()
                        : null
        );

        response.setProductId(
                review.getProduct() != null
                        ? review.getProduct().getId()
                        : null
        );

        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());

        return response;
    }
}