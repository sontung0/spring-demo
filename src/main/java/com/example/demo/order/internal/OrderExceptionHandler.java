package com.example.demo.order.internal;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.order.DuplicateOrderCodeException;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(DuplicateOrderCodeException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateOrderCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}