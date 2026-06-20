package com.example.demo.order;

import org.springframework.modulith.events.Externalized;

@Externalized("orders::#{#this.order.id}")
public record OrderCreated(Order order) {
}