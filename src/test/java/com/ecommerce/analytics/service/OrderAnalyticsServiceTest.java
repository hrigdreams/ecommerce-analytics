package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.OrderAnalytics;
import com.ecommerce.analytics.entity.OrderItemAnalytics;
import com.ecommerce.analytics.event.payload.order.OrderCreatedEvent;
import com.ecommerce.analytics.event.payload.order.OrderItemEvent;
import com.ecommerce.analytics.repository.OrderAnalyticsRepository;
import com.ecommerce.analytics.repository.OrderItemAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderAnalyticsServiceTest {

    private OrderAnalyticsRepository orderRepository;
    private OrderItemAnalyticsRepository itemRepository;
    private ProductAnalyticsService productAnalyticsService;
    private CustomerAnalyticsService customerAnalyticsService;
    private TimeAnalyticsService timeAnalyticsService;
    private GeoAnalyticsService geoAnalyticsService;
    private OrderAnalyticsService service;

    private final Instant at = Instant.parse("2026-09-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderAnalyticsRepository.class);
        itemRepository = mock(OrderItemAnalyticsRepository.class);
        productAnalyticsService = mock(ProductAnalyticsService.class);
        customerAnalyticsService = mock(CustomerAnalyticsService.class);
        timeAnalyticsService = mock(TimeAnalyticsService.class);
        geoAnalyticsService = mock(GeoAnalyticsService.class);
        service = new OrderAnalyticsService(
                orderRepository, itemRepository, productAnalyticsService,
                customerAnalyticsService, timeAnalyticsService, geoAnalyticsService
        );
    }

    @Test
    void recordOrderCreated_shouldComputeTotalFromItemsInBigDecimal() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                1L, 7L, "PENDING", "UNPAID",
                new BigDecimal("999.9900000000002"),   // drifted double total from the source
                at,
                List.of(
                        new OrderItemEvent(1L, 10L, "Phone", 2, 499.99),
                        new OrderItemEvent(2L, 11L, "Case", 1, 0.01)
                )
        );
        when(orderRepository.existsById(1L)).thenReturn(false);

        service.recordOrderCreated(event, at);

        ArgumentCaptor<OrderAnalytics> captor = ArgumentCaptor.forClass(OrderAnalytics.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(new BigDecimal("999.99"), captor.getValue().getTotalAmount());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(false, captor.getValue().isPaid());
        verify(itemRepository).saveAll(any());
    }

    @Test
    void recordOrderCreated_shouldSkipWhenOrderAlreadyRecorded() {
        when(orderRepository.existsById(1L)).thenReturn(true);

        service.recordOrderCreated(
                new OrderCreatedEvent(1L, 7L, "PENDING", "UNPAID", BigDecimal.ONE, at, List.of()),
                at
        );

        verify(orderRepository, never()).save(any());
    }

    @Test
    void recordPayment_paid_shouldMarkPaidAndAddSalesOnce() {
        OrderAnalytics order = new OrderAnalytics();
        order.setOrderId(1L);

        OrderItemAnalytics item = new OrderItemAnalytics();
        item.setOrderId(1L);
        item.setProductId(10L);
        item.setProductName("Phone");
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("999.98"));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(1L)).thenReturn(List.of(item));

        service.recordPayment(1L, "PAID", at);

        assertTrue(order.isPaid());
        assertEquals("PAID", order.getPaymentStatus());
        verify(productAnalyticsService).addSale(10L, "Phone", 2L, new BigDecimal("999.98"), at);

        // A second PAID for the same order must not count revenue again.
        service.recordPayment(1L, "PAID", at);
        verify(productAnalyticsService).addSale(10L, "Phone", 2L, new BigDecimal("999.98"), at);
    }

    @Test
    void recordPayment_failed_shouldNotCountRevenue() {
        OrderAnalytics order = new OrderAnalytics();
        order.setOrderId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        service.recordPayment(1L, "FAILED", at);

        assertEquals(false, order.isPaid());
        assertEquals("FAILED", order.getPaymentStatus());
        verify(productAnalyticsService, never()).addSale(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void recordPayment_unknownOrder_shouldBeSkipped() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        service.recordPayment(99L, "PAID", at);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void recordStatusChange_shouldUpdateStatus() {
        OrderAnalytics order = new OrderAnalytics();
        order.setOrderId(1L);
        order.setStatus("PENDING");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        service.recordStatusChange(1L, "SHIPPED", at);

        assertEquals("SHIPPED", order.getStatus());
        verify(orderRepository).save(order);
    }
}
