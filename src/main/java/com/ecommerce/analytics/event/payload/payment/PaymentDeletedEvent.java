package com.ecommerce.analytics.event.payload.payment;

public class PaymentDeletedEvent {

    private Long paymentId;
    private Long orderId;

    public PaymentDeletedEvent() {
    }

    public PaymentDeletedEvent(Long paymentId, Long orderId) {
        this.paymentId = paymentId;
        this.orderId = orderId;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getOrderId() { return orderId; }

    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}