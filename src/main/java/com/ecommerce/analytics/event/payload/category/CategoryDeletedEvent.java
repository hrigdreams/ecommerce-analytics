package com.ecommerce.analytics.event.payload.category;

public class CategoryDeletedEvent {

    private Long categoryId;

    public CategoryDeletedEvent() {
    }

    public CategoryDeletedEvent(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}