package com.example.demo.user.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.User;
import com.example.demo.user.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService userService;
  
  @PostMapping
  public ResponseEntity<CreateUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    User user = userService.createUser(request.name(), request.email());

    return ResponseEntity.ok(new CreateUserResponse(user));
  }

  @GetMapping
  public ResponseEntity<Iterable<User>> getAll() {
    return ResponseEntity.ok(userService.getAllUsers());
  }
}