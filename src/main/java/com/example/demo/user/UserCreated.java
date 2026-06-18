package com.example.demo.user;

import org.springframework.modulith.events.Externalized;

@Externalized("users::#{#this.user.id}") 
public record UserCreated(User user) {
}
