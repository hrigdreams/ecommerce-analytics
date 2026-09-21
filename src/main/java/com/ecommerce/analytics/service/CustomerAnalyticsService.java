package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CustomerAnalytics;
import com.ecommerce.analytics.repository.CustomerAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class CustomerAnalyticsService {

    private final CustomerAnalyticsRepository repository;

    public CustomerAnalyticsService(CustomerAnalyticsRepository repository) {
        this.repository = repository;
    }

    private CustomerAnalytics getOrCreate(Long userId) {
        return repository.findById(userId).orElseGet(() -> {
            CustomerAnalytics row = new CustomerAnalytics();
            row.setUserId(userId);
            return row;
        });
    }

    /** Called from OrderAnalyticsService.recordOrderCreated for every new order. */
    @Transactional
    public void recordOrderCreated(Long userId, Instant occurredAt) {
        if (userId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CustomerAnalytics row = getOrCreate(userId);
        row.setTotalOrders(row.getTotalOrders() + 1);
        if (row.getFirstOrderAt() == null || at.isBefore(row.getFirstOrderAt())) {
            row.setFirstOrderAt(at);
        }
        if (row.getLastOrderAt() == null || at.isAfter(row.getLastOrderAt())) {
            row.setLastOrderAt(at);
        }
        row.setUpdatedAt(at);
        repository.save(row);
    }

    /** Called from OrderAnalyticsService.recordPayment the first time an order becomes PAID. */
    @Transactional
    public void recordOrderPaid(Long userId, BigDecimal amount, Instant occurredAt) {
        if (userId == null) {
            return;
        }
        Instant at = occurredAt != null ? occurredAt : Instant.now();

        CustomerAnalytics row = getOrCreate(userId);
        row.setPaidOrders(row.getPaidOrders() + 1);
        row.setTotalSpend(row.getTotalSpend().add(amount != null ? amount : BigDecimal.ZERO));
        row.setUpdatedAt(at);
        repository.save(row);
    }
}
