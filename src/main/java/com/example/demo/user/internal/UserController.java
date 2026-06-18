package com.example.demo.user.internal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.user.User;
import com.example.demo.user.UserService;

import lombok.RequiredArgsConstructor;

@Controller // This means that this class is a Controller
@RequestMapping(path = "/users") // This means URL's start with /users (after Application path)
@RequiredArgsConstructor
public class UserController {
  private final UserService userService;
  
  @PostMapping(path = "/add") // Map ONLY POST Requests
  public @ResponseBody String addNewUser(@RequestParam String name, @RequestParam String email) {
    // @ResponseBody means the returned String is the response, not a view name
    // @RequestParam means it is a parameter from the GET or POST request

    User n = userService.createUser(name, email);

    return "Saved user with id: " + n.id;
  }

  @GetMapping(path = "/all")
  public @ResponseBody Iterable<User> getAllUsers() {
    // This returns a JSON or XML with the users
    return userService.getAllUsers();
  }
}