package com.example.demo.user.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
    @NotBlank(message = "Name is required") String name,
    @Email(message = "Email should be valid") String email,
    @NotEmpty(message = "Books list cannot be empty") @Valid List<@Valid @NotNull(message = "Book cannot be null") Book> books
) {
    record Book(
        @NotBlank(message = "Name is required") String name
    ) {
    }
}
