package com.example.demo.user;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public User createUser(String name, String email) {
        User n = new User();
        n.name = name;
        n.email = email;
        userRepository.save(n);

        eventPublisher.publishEvent(new UserCreated(n));

        return n;
    }

    public Iterable<User> getAllUsers() {
        return userRepository.findAll();
    }
}
