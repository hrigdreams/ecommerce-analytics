package com.ecommerce.analytics.event.payload.order;

public class OrderStatusChangedEvent {

    private Long orderId;
    private String previousStatus;
    private String newStatus;

    public OrderStatusChangedEvent() {
    }

    public OrderStatusChangedEvent(
            Long orderId,
            String previousStatus,
            String newStatus
    ) {
        this.orderId = orderId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public Long getOrderId() { return orderId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }

    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
}