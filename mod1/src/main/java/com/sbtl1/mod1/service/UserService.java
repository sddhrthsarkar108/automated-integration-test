package com.sbtl1.mod1.service;

import com.sbtl1.mod1.dao.UserRepository;
import com.sbtl1.mod1.entities.User;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsersByAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
        return userRepository.findByAgeGreaterThan(age);
    }

    public User saveUser(User user) {
        if (user.getName().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        return userRepository.save(user);
    }
}
