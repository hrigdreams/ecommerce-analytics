package com.ecommerce.analytics.event.payload.order;

public class OrderDeletedEvent {

    private Long orderId;
    private Long userId;

    public OrderDeletedEvent() {
    }

    public OrderDeletedEvent(Long orderId, Long userId) {
        this.orderId = orderId;
        this.userId = userId;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }

    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setUserId(Long userId) { this.userId = userId; }
}