package com.ecommerce.analytics.event.payload.payment;

import java.time.Instant;

public class PaymentCreatedEvent {

    private Long paymentId;
    private Long orderId;
    private String paymentMethod;
    private String status;
    private Double amount;
    private String transactionId;
    private Instant createdAt;

    public PaymentCreatedEvent() {
    }

    public PaymentCreatedEvent(
            Long paymentId,
            Long orderId,
            String paymentMethod,
            String status,
            Double amount,
            String transactionId,
            Instant createdAt
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.amount = amount;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getOrderId() { return orderId; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public Double getAmount() { return amount; }
    public String getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setStatus(String status) { this.status = status; }
    public void setAmount(Double amount) { this.amount = amount; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}