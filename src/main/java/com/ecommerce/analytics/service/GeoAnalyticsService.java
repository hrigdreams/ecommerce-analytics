package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.GeoAnalytics;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.repository.GeoAnalyticsRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class GeoAnalyticsService {

    private final GeoAnalyticsRepository repository;
    private final UserRepository userRepository;

    public GeoAnalyticsService(GeoAnalyticsRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    private String[] resolveLocation(Long userId) {
        if (userId == null) {
            return new String[] { GeoAnalytics.UNKNOWN, GeoAnalytics.UNKNOWN };
        }
        return userRepository.findById(userId)
                .map((User u) -> new String[] {
                        u.getCountry() != null ? u.getCountry() : GeoAnalytics.UNKNOWN,
                        u.getCity() != null ? u.getCity() : GeoAnalytics.UNKNOWN
                })
                .orElse(new String[] { GeoAnalytics.UNKNOWN, GeoAnalytics.UNKNOWN });
    }

    private GeoAnalytics getOrCreate(String country, String city, Instant at) {
        return repository.findByCountryAndCity(country, city).orElseGet(() -> {
            GeoAnalytics row = new GeoAnalytics();
            row.setCountry(country);
            row.setCity(city);
            row.setUpdatedAt(at);
            return row;
        });
    }

    /** Called from OrderAnalyticsService.recordOrderCreated for every new order. */
    @Transactional
    public void recordOrderCreated(Long userId, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        String[] location = resolveLocation(userId);

        GeoAnalytics row = getOrCreate(location[0], location[1], at);
        row.setOrders(row.getOrders() + 1);
        row.setUpdatedAt(at);
        repository.save(row);
    }

    /** Called from OrderAnalyticsService.recordPayment the first time an order becomes PAID. */
    @Transactional
    public void recordOrderPaid(Long userId, BigDecimal amount, Instant occurredAt) {
        Instant at = occurredAt != null ? occurredAt : Instant.now();
        String[] location = resolveLocation(userId);

        GeoAnalytics row = getOrCreate(location[0], location[1], at);
        row.setPaidOrders(row.getPaidOrders() + 1);
        row.setRevenue(row.getRevenue().add(amount != null ? amount : BigDecimal.ZERO));
        row.setUpdatedAt(at);
        repository.save(row);
    }
}
