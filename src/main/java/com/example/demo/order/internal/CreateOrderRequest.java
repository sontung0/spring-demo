package com.example.demo.order.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
    @NotBlank(message = "code must not be blank")
    String code,

    @NotEmpty(message = "skus must not be empty")
    @Valid
    List<SkuLine> skus
) {
    public record SkuLine(
        @NotBlank(message = "sku code must not be blank")
        String code,

        @Positive(message = "quantity must be positive")
        Integer quantity
    ) {
    }
}