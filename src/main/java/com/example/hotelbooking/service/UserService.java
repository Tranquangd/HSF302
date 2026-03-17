package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.exception.ResourceNotFoundException;
import com.example.hotelbooking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getUsersByRole(String role) {
        return userRepository.findByRole(role);
    }

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Transactional
    public User createUser(String userId, String password, String username, String role) {
        if (userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User ID already exists: " + userId);
        }

        User user = new User();
        user.setUserId(userId);
        user.setPassword(password);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus("active");

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(String userId, String status) {
        User user = getUserById(userId);
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(String userId, String username, String role, String status) {
        User user = getUserById(userId);

        if (username != null) {
            user.setUsername(username);
        }
        if (role != null) {
            user.setRole(role);
        }
        if (status != null) {
            user.setStatus(status);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(String userId) {
        User user = getUserById(userId);
        userRepository.delete(user);
    }
}

