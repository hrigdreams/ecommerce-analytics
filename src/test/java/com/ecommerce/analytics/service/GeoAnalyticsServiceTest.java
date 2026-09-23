package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.GeoAnalytics;
import com.ecommerce.analytics.entity.User;
import com.ecommerce.analytics.repository.GeoAnalyticsRepository;
import com.ecommerce.analytics.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoAnalyticsServiceTest {

    private GeoAnalyticsRepository repository;
    private UserRepository userRepository;
    private GeoAnalyticsService service;
    private final Instant at = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(GeoAnalyticsRepository.class);
        userRepository = mock(UserRepository.class);
        service = new GeoAnalyticsService(repository, userRepository);
    }

    @Test
    void recordOrderCreated_shouldBucketByUsersCountryAndCity() {
        User user = new User("Alice", "a@x.com", 30, "F", "Kathmandu", "Nepal");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(repository.findByCountryAndCity("Nepal", "Kathmandu")).thenReturn(Optional.empty());

        service.recordOrderCreated(7L, at);

        verify(repository).save(argThat(g ->
                "Nepal".equals(g.getCountry())
                        && "Kathmandu".equals(g.getCity())
                        && g.getOrders() == 1));
    }

    @Test
    void recordOrderCreated_shouldFallBackToUnknownWhenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findByCountryAndCity(GeoAnalytics.UNKNOWN, GeoAnalytics.UNKNOWN))
                .thenReturn(Optional.empty());

        service.recordOrderCreated(99L, at);

        verify(repository).save(argThat(g ->
                GeoAnalytics.UNKNOWN.equals(g.getCountry())
                        && GeoAnalytics.UNKNOWN.equals(g.getCity())));
    }

    @Test
    void recordOrderPaid_shouldAccumulateRevenueForExistingRow() {
        User user = new User("Alice", "a@x.com", 30, "F", "Kathmandu", "Nepal");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        GeoAnalytics row = new GeoAnalytics();
        row.setCountry("Nepal");
        row.setCity("Kathmandu");
        row.setPaidOrders(2);
        row.setRevenue(new BigDecimal("50.00"));
        when(repository.findByCountryAndCity("Nepal", "Kathmandu")).thenReturn(Optional.of(row));

        service.recordOrderPaid(7L, new BigDecimal("49.99"), at);

        assertEquals(3, row.getPaidOrders());
        assertEquals(new BigDecimal("99.99"), row.getRevenue());
    }

    private static GeoAnalytics argThat(java.util.function.Predicate<GeoAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
