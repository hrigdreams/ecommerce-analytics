package com.ecommerce.analytics.service;
import com.ecommerce.analytics.dto.ReviewRequest;
import com.ecommerce.analytics.dto.ReviewResponse;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.entity.Review;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.exception.ProductNotFoundException;
import com.ecommerce.analytics.exception.ReviewNotFoundException;
import com.ecommerce.analytics.exception.UserNotFoundException;
import com.ecommerce.analytics.repository.ProductRepository;
import com.ecommerce.analytics.repository.ReviewRepository;
import com.ecommerce.analytics.repository.UserRepository;
import com.ecommerce.analytics.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private User user;

    @Mock
    private Product product;

    @Mock
    private Review review;

    @InjectMocks
    private ReviewService reviewService;


    @Test
    void createReview_shouldCreateReviewSuccessfully() {

        ReviewRequest request = new ReviewRequest(
                1L,
                1L,
                5,
                "Excellent product"
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        when(user.getId())
                .thenReturn(1L);

        when(product.getId())
                .thenReturn(1L);

        Review savedReview = new Review(
                user,
                product,
                5,
                "Excellent product"
        );

        when(reviewRepository.save(any(Review.class)))
                .thenReturn(savedReview);

        ReviewResponse response =
                reviewService.createReview(request);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(1L, response.getProductId());
        assertEquals(5, response.getRating());
        assertEquals(
                "Excellent product",
                response.getComment()
        );

        verify(userRepository).findById(1L);
        verify(productRepository).findById(1L);
        verify(reviewRepository).save(any(Review.class));
    }


    @Test
    void createReview_shouldThrowUserNotFoundException() {

        ReviewRequest request = new ReviewRequest(
                999L,
                1L,
                5,
                "Test review"
        );

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> reviewService.createReview(request)
        );

        verify(userRepository).findById(999L);

        verify(
                productRepository,
                never()
        ).findById(anyLong());

        verify(
                reviewRepository,
                never()
        ).save(any(Review.class));
    }


    @Test
    void createReview_shouldThrowProductNotFoundException() {

        ReviewRequest request = new ReviewRequest(
                1L,
                999L,
                5,
                "Test review"
        );

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> reviewService.createReview(request)
        );

        verify(userRepository).findById(1L);

        verify(productRepository)
                .findById(999L);

        verify(
                reviewRepository,
                never()
        ).save(any(Review.class));
    }


    @Test
    void getReviewById_shouldReturnReviewSuccessfully() {

        when(reviewRepository.findById(1L))
                .thenReturn(Optional.of(review));

        when(review.getUser())
                .thenReturn(user);

        when(review.getProduct())
                .thenReturn(product);

        when(user.getId())
                .thenReturn(1L);

        when(product.getId())
                .thenReturn(1L);

        when(review.getRating())
                .thenReturn(5);

        when(review.getComment())
                .thenReturn("Excellent product");

        ReviewResponse response =
                reviewService.getReviewById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals(1L, response.getProductId());
        assertEquals(5, response.getRating());
        assertEquals(
                "Excellent product",
                response.getComment()
        );

        verify(reviewRepository)
                .findById(1L);
    }


    @Test
    void getReviewById_shouldThrowReviewNotFoundException() {

        when(reviewRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ReviewNotFoundException.class,
                () -> reviewService.getReviewById(999L)
        );

        verify(reviewRepository)
                .findById(999L);
    }


    @Test
    void deleteReview_shouldDeleteReviewSuccessfully() {

        when(reviewRepository.findById(1L))
                .thenReturn(Optional.of(review));

        reviewService.deleteReview(1L);

        verify(reviewRepository)
                .findById(1L);

        verify(reviewRepository)
                .delete(review);
    }


    @Test
    void deleteReview_shouldThrowReviewNotFoundException() {

        when(reviewRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ReviewNotFoundException.class,
                () -> reviewService.deleteReview(999L)
        );

        verify(reviewRepository)
                .findById(999L);

        verify(
                reviewRepository,
                never()
        ).delete(any(Review.class));
    }
}