package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CategoryAnalytics;
import com.ecommerce.analytics.repository.CategoryAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryAnalyticsServiceTest {

    private CategoryAnalyticsRepository repository;
    private CategoryAnalyticsService service;
    private final Instant at = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(CategoryAnalyticsRepository.class);
        service = new CategoryAnalyticsService(repository);
    }

    @Test
    void upsertCategory_shouldCreateRowWhenMissing() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.upsertCategory(1L, "Phones", at);

        verify(repository).save(argThat(c -> "Phones".equals(c.getCategoryName())));
    }

    @Test
    void upsertCategory_shouldSkipWhenCategoryIdNull() {
        service.upsertCategory(null, "Phones", at);
        verify(repository, never()).save(any());
    }

    @Test
    void adjustProductCount_shouldApplyPositiveAndNegativeDelta() {
        CategoryAnalytics row = new CategoryAnalytics();
        row.setCategoryId(1L);
        row.setProductCount(5);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.adjustProductCount(1L, -1, at);

        assertEquals(4, row.getProductCount());
    }

    @Test
    void addSale_shouldAccumulateUnitsAndRevenue() {
        CategoryAnalytics row = new CategoryAnalytics();
        row.setCategoryId(1L);
        row.setUnitsSold(10);
        row.setRevenue(new BigDecimal("100.00"));
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.addSale(1L, 3, new BigDecimal("49.99"), at);

        assertEquals(13, row.getUnitsSold());
        assertEquals(new BigDecimal("149.99"), row.getRevenue());
    }

    @Test
    void addReview_shouldIncrementCountAndRatingSum() {
        CategoryAnalytics row = new CategoryAnalytics();
        row.setCategoryId(1L);
        row.setReviewCount(2);
        row.setRatingSum(8);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.addReview(1L, 5, at);

        assertEquals(3, row.getReviewCount());
        assertEquals(13, row.getRatingSum());
    }

    @Test
    void updateReview_shouldAdjustRatingSumByDifference() {
        CategoryAnalytics row = new CategoryAnalytics();
        row.setCategoryId(1L);
        row.setRatingSum(10);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.updateReview(1L, 2, 5, at);

        assertEquals(13, row.getRatingSum());
    }

    @Test
    void removeReview_shouldDecrementCountAndRatingSum() {
        CategoryAnalytics row = new CategoryAnalytics();
        row.setCategoryId(1L);
        row.setReviewCount(3);
        row.setRatingSum(12);
        when(repository.findById(1L)).thenReturn(Optional.of(row));

        service.removeReview(1L, 4, at);

        assertEquals(2, row.getReviewCount());
        assertEquals(8, row.getRatingSum());
    }

    private static CategoryAnalytics argThat(java.util.function.Predicate<CategoryAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
