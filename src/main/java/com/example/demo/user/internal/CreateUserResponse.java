package com.example.demo.user.internal;

import com.example.demo.user.User;

public record CreateUserResponse(
    Integer id, 
    String name, 
    String email
) {
    public CreateUserResponse(User user) {
        this(user.id, user.name, user.email);
    }
}
