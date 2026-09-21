package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CategoryAnalytics;
import com.ecommerce.analytics.repository.CategoryAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class CategoryAnalyticsService {

    private final CategoryAnalyticsRepository repository;

    public CategoryAnalyticsService(CategoryAnalyticsRepository repository) {
        this.repository = repository;
    }

    private CategoryAnalytics getOrCreate(Long categoryId, Instant at) {
        return repository.findById(categoryId).orElseGet(() -> {
            CategoryAnalytics row = new CategoryAnalytics();
            row.setCategoryId(categoryId);
            row.setUpdatedAt(at);
            return row;
        });
    }

    /** CATEGORY_CREATED / CATEGORY_UPDATED: keep the display name in sync. */
    @Transactional
    public void upsertCategory(Long categoryId, String name, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setCategoryName(name);
        row.setUpdatedAt(at);
        repository.save(row);
    }

    /**
     * Called from ProductAnalyticsService when a product is created/deleted or
     * moved between categories. delta is +1 or -1.
     */
    @Transactional
    public void adjustProductCount(Long categoryId, long delta, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setProductCount(row.getProductCount() + delta);
        row.setUpdatedAt(at);
        repository.save(row);
    }

    /** Called when a product in this category is part of a PAID order. */
    @Transactional
    public void addSale(Long categoryId, long quantity, BigDecimal lineTotal, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setUnitsSold(row.getUnitsSold() + quantity);
        row.setRevenue(row.getRevenue().add(lineTotal));
        row.setUpdatedAt(at);
        repository.save(row);
    }

    @Transactional
    public void addReview(Long categoryId, int rating, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setReviewCount(row.getReviewCount() + 1);
        row.setRatingSum(row.getRatingSum() + rating);
        row.setUpdatedAt(at);
        repository.save(row);
    }

    @Transactional
    public void updateReview(Long categoryId, int oldRating, int newRating, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setRatingSum(row.getRatingSum() + (newRating - oldRating));
        row.setUpdatedAt(at);
        repository.save(row);
    }

    @Transactional
    public void removeReview(Long categoryId, int rating, Instant occurredAt) {
        if (categoryId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        CategoryAnalytics row = getOrCreate(categoryId, at);
        row.setReviewCount(row.getReviewCount() - 1);
        row.setRatingSum(row.getRatingSum() - rating);
        row.setUpdatedAt(at);
        repository.save(row);
    }
}
