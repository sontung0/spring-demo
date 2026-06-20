package com.example.demo.order.internal;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.order.Order;
import com.example.demo.order.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderService.SkuLine> skuLines = request.skus().stream()
            .map(s -> new OrderService.SkuLine(s.code(), s.quantity()))
            .toList();

        Order order = orderService.createOrder(request.code(), skuLines);

        return ResponseEntity.ok(new CreateOrderResponse(order));
    }
}