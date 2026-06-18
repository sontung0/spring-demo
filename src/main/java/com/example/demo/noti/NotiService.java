package com.example.demo.noti;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import com.example.demo.user.UserCreated;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotiService {

    @ApplicationModuleListener
    public void on(UserCreated event) {
        System.out.println("Received UserCreated event for user: " + event.user().id + ", name: " + event.user().name);
        // throw new RuntimeException("Simulated failure in NotiService");
    }
}
