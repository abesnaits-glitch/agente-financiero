package com.agentefinanciero.controller;

import com.agentefinanciero.model.NotificacionLanzamiento;
import com.agentefinanciero.repository.NotificacionLanzamientoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
public class NotificacionLanzamientoController {

    private static final Set<String> AGENTES_VALIDOS = Set.of("vita", "lilith", "nexo");

    private final NotificacionLanzamientoRepository repo;

    public NotificacionLanzamientoController(NotificacionLanzamientoRepository repo) {
        this.repo = repo;
    }

    @PostMapping("/api/notificar-lanzamiento")
    public ResponseEntity<?> registrar(@RequestBody Map<String, String> body) {
        String agente    = sanitize(body.get("agente"),    50);
        String email     = sanitize(body.get("email"),    254);
        String whatsapp  = sanitize(body.get("whatsapp"),  30);

        if (agente == null || !AGENTES_VALIDOS.contains(agente.toLowerCase()))
            return ResponseEntity.badRequest().body(Map.of("error", "agente inválido"));

        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return ResponseEntity.badRequest().body(Map.of("error", "email inválido"));

        agente = agente.toLowerCase();

        if (repo.findByEmailIgnoreCaseAndAgente(email, agente).isPresent())
            return ResponseEntity.ok(Map.of("duplicado", true));

        NotificacionLanzamiento n = new NotificacionLanzamiento();
        n.setAgente(agente);
        n.setEmail(email);
        n.setWhatsapp(whatsapp != null && !whatsapp.isBlank() ? whatsapp : null);
        repo.save(n);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String sanitize(String s, int max) {
        if (s == null) return null;
        s = s.strip();
        return s.isEmpty() ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
