package com.ecommerce.analytics.analytics.service;

import com.ecommerce.analytics.entity.ProductAnalytics;
import com.ecommerce.analytics.repository.ProductAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class ProductAnalyticsService {

    private final ProductAnalyticsRepository repository;

    public ProductAnalyticsService(ProductAnalyticsRepository repository) {
        this.repository = repository;
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

        ProductAnalytics row = repository.findById(productId).orElseGet(() -> {
            ProductAnalytics created = new ProductAnalytics();
            created.setProductId(productId);
            created.setCreatedAt(at);
            return created;
        });

        row.setProductName(name);
        row.setCategoryId(categoryId);
        row.setPrice(price != null ? BigDecimal.valueOf(price) : null);
        row.setActive(true);
        row.setUpdatedAt(at);

        repository.save(row);
    }

    /** PRODUCT_DELETED: keep history (sales/reviews), just flag inactive. */
    @Transactional
    public void markDeleted(Long productId, Instant occurredAt) {
        repository.findById(productId).ifPresent(row -> {
            row.setActive(false);
            row.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
            repository.save(row);
        });
    }
}
