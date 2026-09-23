package com.ecommerce.analytics.event.payload.funnel;

/**
 * Emitted the moment a user submits their cart for checkout, before the order
 * is validated or persisted. Lets funnel analytics see "started checkout but
 * never completed" without depending on ORDER_CREATED succeeding.
 */
public class CheckoutStartedEvent {

    private Long userId;
    private int itemCount;

    public CheckoutStartedEvent() {
    }

    public CheckoutStartedEvent(Long userId, int itemCount) {
        this.userId = userId;
        this.itemCount = itemCount;
    }

    public Long getUserId() { return userId; }
    public int getItemCount() { return itemCount; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
}
