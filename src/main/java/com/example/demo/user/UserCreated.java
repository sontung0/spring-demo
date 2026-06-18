package com.example.demo.user;

import org.springframework.modulith.events.Externalized;

@Externalized("users::#{#id}") 
public record UserCreated(User user) {
    
}
