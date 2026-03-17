package com.example.hotelbooking.controller;

import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.service.RoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final RoomService roomService;

    public HomeController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Room> availableRooms = roomService.getAllRooms().stream()
                .filter(r -> Boolean.TRUE.equals(r.getAvailable()))
                .collect(Collectors.toList());
        model.addAttribute("rooms", availableRooms);
        return "index";
    }
}
