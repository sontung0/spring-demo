package com.example.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.demo.order.internal.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    OrderService orderService;

    @Test
    void createOrder_persistsOrderWithNewStatusAndSkus_andPublishesEvent() {
        when(orderRepository.existsByCode("ORD-1")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(
            "ORD-1",
            List.of(new OrderService.SkuLine("SKU-A", 2))
        );

        assertThat(result.code).isEqualTo("ORD-1");
        assertThat(result.status).isEqualTo(OrderStatus.NEW);
        assertThat(result.skus).hasSize(1);
        assertThat(result.skus.get(0).code).isEqualTo("SKU-A");
        assertThat(result.skus.get(0).quantity).isEqualTo(2);

        verify(orderRepository).save(any(Order.class));

        ArgumentCaptor<OrderCreated> eventCaptor = ArgumentCaptor.forClass(OrderCreated.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().order()).isEqualTo(result);
    }

    @Test
    void createOrder_throwsDuplicateOrderCodeException_whenCodeExists() {
        when(orderRepository.existsByCode("DUP")).thenReturn(true);

        assertThatThrownBy(() -> orderService.createOrder("DUP", List.of(new OrderService.SkuLine("SKU-A", 1))))
            .isInstanceOf(DuplicateOrderCodeException.class)
            .hasMessageContaining("DUP");

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}