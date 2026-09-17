package com.ecommerce.analytics.exception;

public class ProductInUseException extends RuntimeException {

    public ProductInUseException(String message) {
        super(message);
    }
}