package com.ecommerce.analytics.event.payload.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderCreatedEvent {

    private Long orderId;
    private Long userId;
    private String status;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private List<OrderItemEvent> items = new ArrayList<>();

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(
            Long orderId,
            Long userId,
            String status,
            String paymentStatus,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemEvent> items
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.items = items;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItemEvent> getItems() { return items; }

    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setStatus(String status) { this.status = status; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setItems(List<OrderItemEvent> items) { this.items = items; }
}