package com.example.demo.order.internal;

import org.springframework.data.repository.CrudRepository;

import com.example.demo.order.Order;

public interface OrderRepository extends CrudRepository<Order, Integer> {
    boolean existsByCode(String code);
}