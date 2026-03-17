package com.example.hotelbooking.controller;

import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.repository.RoomRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {
    
    private final RoomRepository roomRepository;
    
    public TestController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    @GetMapping("/rooms")
    public Map<String, Object> testRooms() {
        Map<String, Object> result = new HashMap<>();
        
        // Test 1: Count all rooms
        long totalRooms = roomRepository.count();
        result.put("totalRooms", totalRooms);
        
        // Test 2: Get all rooms
        List<Room> allRooms = roomRepository.findAll();
        result.put("allRooms", allRooms);
        
        // Test 3: Get available rooms
        List<Room> availableRooms = roomRepository.findAllAvailableRooms();
        result.put("availableRooms", availableRooms);
        result.put("availableCount", availableRooms.size());
        
        return result;
    }
}
