package com.example.demo.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.demo.order.DuplicateOrderCodeException;
import com.example.demo.order.Order;
import com.example.demo.order.OrderService;
import com.example.demo.order.OrderStatus;

class OrderControllerTest {

    private OrderController orderController;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        orderController = new OrderController(orderService);
    }

    @Test
    void create_returns200WithOrderResponse_onValidRequest() {
        Order order = new Order();
        order.id = 1;
        order.code = "ORD-1";
        order.status = OrderStatus.NEW;

        com.example.demo.order.OrderSku sku = new com.example.demo.order.OrderSku();
        sku.code = "SKU-A";
        sku.quantity = 2;
        order.skus.add(sku);

        when(orderService.createOrder(eq("ORD-1"), anyList())).thenReturn(order);

        CreateOrderRequest request = new CreateOrderRequest(
            "ORD-1",
            List.of(new CreateOrderRequest.SkuLine("SKU-A", 2))
        );

        var response = orderController.create(request);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody().code()).isEqualTo("ORD-1");
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getBody().skus()).hasSize(1);
        assertThat(response.getBody().skus().get(0).code()).isEqualTo("SKU-A");
        assertThat(response.getBody().skus().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void create_throwsExceptionOnDuplicate() {
        when(orderService.createOrder(eq("DUP"), anyList()))
            .thenThrow(new DuplicateOrderCodeException("DUP"));

        CreateOrderRequest request = new CreateOrderRequest(
            "DUP",
            List.of(new CreateOrderRequest.SkuLine("SKU-A", 1))
        );

        try {
            orderController.create(request);
        } catch (DuplicateOrderCodeException e) {
            assertThat(e.getMessage()).contains("DUP");
        }
    }
}