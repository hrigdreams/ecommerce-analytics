package com.ecommerce.analytics.service;

import com.ecommerce.analytics.entity.OrderAnalytics;
import com.ecommerce.analytics.entity.OrderItemAnalytics;
import com.ecommerce.analytics.event.payload.order.OrderCreatedEvent;
import com.ecommerce.analytics.event.payload.order.OrderItemEvent;
import com.ecommerce.analytics.repository.OrderAnalyticsRepository;
import com.ecommerce.analytics.repository.OrderItemAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsService.class);

    private final OrderAnalyticsRepository orderRepository;
    private final OrderItemAnalyticsRepository itemRepository;
    private final ProductAnalyticsService productAnalyticsService;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final TimeAnalyticsService timeAnalyticsService;
    private final GeoAnalyticsService geoAnalyticsService;

    public OrderAnalyticsService(
            OrderAnalyticsRepository orderRepository,
            OrderItemAnalyticsRepository itemRepository,
            ProductAnalyticsService productAnalyticsService,
            CustomerAnalyticsService customerAnalyticsService,
            TimeAnalyticsService timeAnalyticsService,
            GeoAnalyticsService geoAnalyticsService
    ) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.productAnalyticsService = productAnalyticsService;
        this.customerAnalyticsService = customerAnalyticsService;
        this.timeAnalyticsService = timeAnalyticsService;
        this.geoAnalyticsService = geoAnalyticsService;
    }

    /** ORDER_CREATED. The total is recomputed from the items in BigDecimal (exact). */
    @Transactional
    public void recordOrderCreated(OrderCreatedEvent event, Instant occurredAt) {
        if (orderRepository.existsById(event.getOrderId())) {
            return;
        }

        List<OrderItemEvent> items =
                event.getItems() != null ? event.getItems() : List.of();

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItemAnalytics> rows = new ArrayList<>();

        for (OrderItemEvent item : items) {
            int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
            BigDecimal unitPrice = item.getUnitPrice() != null
                    ? BigDecimal.valueOf(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItemAnalytics row = new OrderItemAnalytics();
            row.setOrderId(event.getOrderId());
            row.setProductId(item.getProductId());
            row.setProductName(item.getProductName());
            row.setQuantity(quantity);
            row.setUnitPrice(unitPrice);
            row.setLineTotal(lineTotal);
            rows.add(row);

            total = total.add(lineTotal);
        }

        Instant at = occurredAt != null ? occurredAt : Instant.now();
        Instant createdAt = event.getCreatedAt() != null ? event.getCreatedAt() : at;

        OrderAnalytics order = new OrderAnalytics();
        order.setOrderId(event.getOrderId());
        order.setUserId(event.getUserId());
        order.setStatus(event.getStatus());
        order.setPaymentStatus(event.getPaymentStatus());
        order.setTotalAmount(total);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(at);

        orderRepository.save(order);
        itemRepository.saveAll(rows);

        customerAnalyticsService.recordOrderCreated(event.getUserId(), createdAt);
        timeAnalyticsService.recordOrderCreated(createdAt);
        geoAnalyticsService.recordOrderCreated(event.getUserId(), createdAt);
    }

    /** ORDER_STATUS_CHANGED. */
    @Transactional
    public void recordStatusChange(Long orderId, String newStatus, Instant occurredAt) {
        OrderAnalytics order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("Order {} not in analytics (created before the pipeline?); status change skipped", orderId);
            return;
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(occurredAt != null ? occurredAt : Instant.now());
        orderRepository.save(order);
    }

    /**
     * PAYMENT_CREATED. Only a PAID payment counts as revenue, and only once per order.
     */
    @Transactional
    public void recordPayment(Long orderId, String paymentStatus, Instant occurredAt) {
        OrderAnalytics order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("Order {} not in analytics (created before the pipeline?); payment skipped", orderId);
            return;
        }

        Instant at = occurredAt != null ? occurredAt : Instant.now();

        order.setPaymentStatus(paymentStatus);
        order.setUpdatedAt(at);

        if ("PAID".equalsIgnoreCase(paymentStatus) && !order.isPaid()) {
            order.setPaid(true);
            order.setPaidAt(at);

            long totalUnits = 0;
            for (OrderItemAnalytics item : itemRepository.findByOrderId(orderId)) {
                productAnalyticsService.addSale(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getLineTotal(),
                        at
                );
                totalUnits += item.getQuantity();
            }

            // Bucketed by the order's own created_at, not the payment time.
            customerAnalyticsService.recordOrderPaid(order.getUserId(), order.getTotalAmount(), order.getCreatedAt());
            timeAnalyticsService.recordOrderPaid(order.getCreatedAt(), order.getTotalAmount(), totalUnits);
            geoAnalyticsService.recordOrderPaid(order.getUserId(), order.getTotalAmount(), order.getCreatedAt());
        }

        orderRepository.save(order);
    }
}
