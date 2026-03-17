package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.ChatContext;
import com.example.hotelbooking.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String chatPage() {
        return "chat";
    }

    @PostMapping("/message")
    @ResponseBody
    public ResponseEntity<ChatService.ChatResponse> processMessage(@RequestBody Map<String, Object> request) {
        String userMessage = (String) request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Extract context from request
        ChatContext previousContext = null;
        if (request.containsKey("context")) {
            try {
                Object contextObj = request.get("context");
                if (contextObj instanceof Map) {
                    previousContext = objectMapper.convertValue(contextObj, ChatContext.class);
                }
            } catch (Exception e) {
                // If context parsing fails, continue without it
            }
        }

        ChatService.ChatResponse response = chatService.processMessage(userMessage, previousContext);
        return ResponseEntity.ok(response);
    }
}
