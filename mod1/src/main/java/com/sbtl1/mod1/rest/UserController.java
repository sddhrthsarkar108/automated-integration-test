package com.sbtl1.mod1.rest;

import com.sbtl1.mod1.entities.User;
import com.sbtl1.mod1.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/age/{age}")
    public ResponseEntity<List<User>> getUsersAboveAge(@PathVariable int age) {
        return ResponseEntity.ok(userService.getUsersByAge(age));
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.saveUser(user));
    }
}
