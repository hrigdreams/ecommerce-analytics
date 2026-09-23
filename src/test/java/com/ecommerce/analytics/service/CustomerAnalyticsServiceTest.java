package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.CustomerAnalytics;
import com.ecommerce.analytics.repository.CustomerAnalyticsRepository;
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

class CustomerAnalyticsServiceTest {

    private CustomerAnalyticsRepository repository;
    private CustomerAnalyticsService service;
    private final Instant first = Instant.parse("2026-09-01T10:00:00Z");
    private final Instant second = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(CustomerAnalyticsRepository.class);
        service = new CustomerAnalyticsService(repository);
    }

    @Test
    void recordOrderCreated_shouldSetFirstAndLastOrderOnFirstOrder() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        service.recordOrderCreated(7L, first);

        verify(repository).save(argThat(c ->
                c.getTotalOrders() == 1
                        && first.equals(c.getFirstOrderAt())
                        && first.equals(c.getLastOrderAt())));
    }

    @Test
    void recordOrderCreated_shouldUpdateLastOrderOnlyForNewerOrder() {
        CustomerAnalytics row = new CustomerAnalytics();
        row.setUserId(7L);
        row.setTotalOrders(1);
        row.setFirstOrderAt(first);
        row.setLastOrderAt(first);
        when(repository.findById(7L)).thenReturn(Optional.of(row));

        service.recordOrderCreated(7L, second);

        assertEquals(2, row.getTotalOrders());
        assertEquals(first, row.getFirstOrderAt());
        assertEquals(second, row.getLastOrderAt());
    }

    @Test
    void recordOrderCreated_shouldSkipWhenUserIdNull() {
        service.recordOrderCreated(null, first);
        verify(repository, never()).save(any());
    }

    @Test
    void recordOrderPaid_shouldAccumulateSpendAndPaidOrders() {
        CustomerAnalytics row = new CustomerAnalytics();
        row.setUserId(7L);
        row.setPaidOrders(1);
        row.setTotalSpend(new BigDecimal("100.00"));
        when(repository.findById(7L)).thenReturn(Optional.of(row));

        service.recordOrderPaid(7L, new BigDecimal("49.99"), second);

        assertEquals(2, row.getPaidOrders());
        assertEquals(new BigDecimal("149.99"), row.getTotalSpend());
    }

    @Test
    void recordOrderPaid_shouldTreatNullAmountAsZero() {
        CustomerAnalytics row = new CustomerAnalytics();
        row.setUserId(7L);
        row.setTotalSpend(new BigDecimal("10.00"));
        when(repository.findById(7L)).thenReturn(Optional.of(row));

        service.recordOrderPaid(7L, null, second);

        assertEquals(new BigDecimal("10.00"), row.getTotalSpend());
    }

    private static CustomerAnalytics argThat(java.util.function.Predicate<CustomerAnalytics> p) {
        return org.mockito.ArgumentMatchers.argThat(p::test);
    }
}
