package com.ecommerce.analytics.event.payload.user;

public class UserDeletedEvent {

    private Long userId;

    public UserDeletedEvent() {
    }

    public UserDeletedEvent(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
