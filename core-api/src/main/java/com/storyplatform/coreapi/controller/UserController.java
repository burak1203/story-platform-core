package com.storyplatform.coreapi.controller;

import com.storyplatform.coreapi.entity.User;
import com.storyplatform.coreapi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    // Resepsiyonist, emirleri iletmek için fabrika müdürünü (Service) çağırır.
    private final UserService userService;

    // Ön yüzden gelecek olan POST isteklerini karşılayan uç nokta (endpoint)
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
        return ResponseEntity.ok(createdUser);
    }
}