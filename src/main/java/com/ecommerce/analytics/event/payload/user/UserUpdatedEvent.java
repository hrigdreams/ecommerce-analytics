package com.ecommerce.analytics.event.payload.user;

public class UserUpdatedEvent {

    private Long userId;

    public UserUpdatedEvent() {
    }

    public UserUpdatedEvent(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

