package com.agentefinanciero.controller;

import com.agentefinanciero.model.ChatRequest;
import com.agentefinanciero.model.ChatResponse;
import com.agentefinanciero.service.ClaudeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ClaudeService claudeService;

    public ChatController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String usuarioId = request.getUsuarioId() != null ? request.getUsuarioId() : "default";
        String respuesta = claudeService.chat(usuarioId, request.getMensaje());
        return ResponseEntity.ok(new ChatResponse(respuesta));
    }
}
