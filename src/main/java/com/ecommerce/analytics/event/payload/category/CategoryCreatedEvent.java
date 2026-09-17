package com.ecommerce.analytics.event.payload.category;

public class CategoryCreatedEvent {

    private Long categoryId;
    private String name;

    public CategoryCreatedEvent() {
    }

    public CategoryCreatedEvent(Long categoryId, String name) {
        this.categoryId = categoryId;
        this.name = name;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void setName(String name) {
        this.name = name;
    }
}