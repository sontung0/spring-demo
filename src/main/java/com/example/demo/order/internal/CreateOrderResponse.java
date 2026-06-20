package com.example.demo.order.internal;

import java.util.List;

import com.example.demo.order.Order;
import com.example.demo.order.OrderStatus;

public record CreateOrderResponse(
    Integer id,
    String code,
    OrderStatus status,
    List<SkuView> skus
) {
    public record SkuView(String code, Integer quantity) {
    }

    public CreateOrderResponse(Order order) {
        this(
            order.id,
            order.code,
            order.status,
            order.skus.stream().map(s -> new SkuView(s.code, s.quantity)).toList()
        );
    }
}