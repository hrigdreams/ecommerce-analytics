package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.ProductAnalytics;
import com.ecommerce.analytics.repository.ProductAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class ProductAnalyticsService {

    private final ProductAnalyticsRepository repository;
    private final CategoryAnalyticsService categoryAnalyticsService;

    public ProductAnalyticsService(
            ProductAnalyticsRepository repository,
            CategoryAnalyticsService categoryAnalyticsService
    ) {
        this.repository = repository;
        this.categoryAnalyticsService = categoryAnalyticsService;
    }

    /** Used for PRODUCT_CREATED and PRODUCT_UPDATED. Safe to run more than once. */
    @Transactional
    public void upsertProduct(
            Long productId,
            String name,
            Long categoryId,
            Double price,
            Instant occurredAt
    ) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        boolean isNew = !repository.existsById(productId);
        ProductAnalytics row = repository.findById(productId).orElseGet(() -> {
            ProductAnalytics created = new ProductAnalytics();
            created.setProductId(productId);
            created.setCreatedAt(at);
            return created;
        });

        Long previousCategoryId = row.getCategoryId();

        row.setProductName(name);
        row.setCategoryId(categoryId);
        row.setPrice(price != null ? BigDecimal.valueOf(price) : null);
        row.setActive(true);
        row.setUpdatedAt(at);

        repository.save(row);

        // Keep category_analytics.product_count in sync (new product, or moved category).
        if (isNew) {
            categoryAnalyticsService.adjustProductCount(categoryId, 1, at);
        } else if (previousCategoryId != null && !previousCategoryId.equals(categoryId)) {
            categoryAnalyticsService.adjustProductCount(previousCategoryId, -1, at);
            categoryAnalyticsService.adjustProductCount(categoryId, 1, at);
        }
    }

    /** PRODUCT_DELETED: keep history (sales/reviews), just flag inactive. */
    @Transactional
    public void markDeleted(Long productId, Instant occurredAt) {
        repository.findById(productId).ifPresent(row -> {
            if (!row.isActive()) {
                return; // already handled (defensive against duplicate delivery)
            }
            row.setActive(false);
            row.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
            repository.save(row);
            categoryAnalyticsService.adjustProductCount(row.getCategoryId(), -1, occurredAt);
        });
    }

    /**
     * Called when an order is PAID: adds units and revenue to the product's totals.
     * Creates a placeholder row if the product was never seen by analytics
     * (e.g. it existed before the analytics pipeline was added).
     */
    @Transactional
    public void addSale(
            Long productId,
            String productName,
            long quantity,
            BigDecimal lineTotal,
            Instant occurredAt
    ) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        ProductAnalytics row = repository.findById(productId).orElseGet(() -> {
            ProductAnalytics created = new ProductAnalytics();
            created.setProductId(productId);
            created.setProductName(productName);
            created.setActive(true);
            created.setCreatedAt(at);
            return created;
        });

        row.setUnitsSold(row.getUnitsSold() + quantity);
        row.setRevenue(row.getRevenue().add(lineTotal));
        row.setUpdatedAt(at);

        repository.save(row);
        categoryAnalyticsService.addSale(row.getCategoryId(), quantity, lineTotal, at);
    }

    /** CART_ITEM_ADDED: bumps how often this product gets added to a cart. */
    @Transactional
    public void recordCartAdd(Long productId, String productName, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        ProductAnalytics row = repository.findById(productId).orElseGet(() -> {
            ProductAnalytics created = new ProductAnalytics();
            created.setProductId(productId);
            created.setProductName(productName);
            created.setActive(true);
            created.setCreatedAt(at);
            return created;
        });

        row.setCartAddCount(row.getCartAddCount() + 1);
        row.setUpdatedAt(at);
        repository.save(row);
    }

    /** CART_ITEM_REMOVED: bumps how often this product gets taken out of a cart. */
    @Transactional
    public void recordCartRemove(Long productId, Instant occurredAt) {
        repository.findById(productId).ifPresent(row -> {
            row.setCartRemoveCount(row.getCartRemoveCount() + 1);
            row.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
            repository.save(row);
        });
    }

    /** REVIEW_CREATED. */
    @Transactional
    public void addReview(Long productId, int rating, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        ProductAnalytics row = repository.findById(productId).orElseGet(() -> {
            ProductAnalytics created = new ProductAnalytics();
            created.setProductId(productId);
            created.setActive(true);
            created.setCreatedAt(at);
            return created;
        });

        row.setReviewCount(row.getReviewCount() + 1);
        row.setRatingSum(row.getRatingSum() + rating);
        row.setUpdatedAt(at);
        repository.save(row);

        categoryAnalyticsService.addReview(row.getCategoryId(), rating, at);
    }

    /** REVIEW_UPDATED: only the rating delta matters for the running sum. */
    @Transactional
    public void updateReview(Long productId, int oldRating, int newRating, Instant occurredAt) {
        repository.findById(productId).ifPresent(row -> {
            row.setRatingSum(row.getRatingSum() + (newRating - oldRating));
            row.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
            repository.save(row);
            categoryAnalyticsService.updateReview(row.getCategoryId(), oldRating, newRating, occurredAt);
        });
    }

    /** REVIEW_DELETED. */
    @Transactional
    public void removeReview(Long productId, int rating, Instant occurredAt) {
        repository.findById(productId).ifPresent(row -> {
            row.setReviewCount(Math.max(0, row.getReviewCount() - 1));
            row.setRatingSum(row.getRatingSum() - rating);
            row.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
            repository.save(row);
            categoryAnalyticsService.removeReview(row.getCategoryId(), rating, occurredAt);
        });
    }
}
