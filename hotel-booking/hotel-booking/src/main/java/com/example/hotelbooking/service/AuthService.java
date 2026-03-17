package com.example.hotelbooking.service;

import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Optional<User> authenticate(String userId, String password) {
        Optional<User> user = userRepository.findByUserIdAndPassword(userId, password);
        
        if (user.isPresent() && "active".equals(user.get().getStatus())) {
            return user;
        }
        
        return Optional.empty();
    }
    
    public boolean isAdmin(User user) {
        return "admin".equals(user.getRole());
    }
    
    public boolean isManager(User user) {
        return "manager".equals(user.getRole());
    }
    
    public boolean isStaff(User user) {
        return "staff".equals(user.getRole());
    }
    
    public boolean isCustomer(User user) {
        return "customer".equals(user.getRole());
    }

    public User register(String userId, String password, String username) {
        if (userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User ID already exists: " + userId);
        }

        User user = new User();
        user.setUserId(userId);
        user.setPassword(password);
        user.setUsername(username);
        user.setRole("customer");
        user.setStatus("active");

        return userRepository.save(user);
    }
}
