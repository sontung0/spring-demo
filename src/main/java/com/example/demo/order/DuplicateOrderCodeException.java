package com.example.demo.order;

public class DuplicateOrderCodeException extends RuntimeException {
    public DuplicateOrderCodeException(String code) {
        super("Order code already exists: " + code);
    }
}