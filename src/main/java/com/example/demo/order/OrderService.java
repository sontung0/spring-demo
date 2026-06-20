package com.example.demo.order;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.order.internal.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    public record SkuLine(String code, Integer quantity) {
    }

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(String code, List<SkuLine> skus) {
        if (orderRepository.existsByCode(code)) {
            throw new DuplicateOrderCodeException(code);
        }

        Order order = new Order();
        order.code = code;
        order.status = OrderStatus.NEW;
        for (SkuLine line : skus) {
            OrderSku sku = new OrderSku();
            sku.code = line.code();
            sku.quantity = line.quantity();
            order.addSku(sku);
        }

        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreated(order));

        return order;
    }
}