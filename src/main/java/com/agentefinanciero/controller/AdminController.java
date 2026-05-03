package com.agentefinanciero.controller;

import com.agentefinanciero.service.LanzamientoService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final Set<String> AGENTES_VALIDOS = Set.of("vita", "lilith", "nexo");

    @Value("${admin.password}")
    private String adminPassword;

    @PostConstruct
    void validarConfig() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD no configurada — arranque abortado");
        }
    }

    private final LanzamientoService lanzamientoService;

    public AdminController(LanzamientoService lanzamientoService) {
        this.lanzamientoService = lanzamientoService;
    }

    @PostMapping("/admin/lanzar-agente")
    public ResponseEntity<?> lanzarAgente(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Faro Admin\"")
                    .body(Map.of("error", "no autorizado"));

        String agente = body.get("agente");
        if (agente == null || agente.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "campo 'agente' requerido"));

        agente = agente.toLowerCase().strip();
        if (!AGENTES_VALIDOS.contains(agente))
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "agente inválido, valores permitidos: vita, lilith, nexo"));

        final String agenteNombre = agente;
        Thread.ofVirtual()
                .name("lanzamiento-" + agenteNombre)
                .start(() -> lanzamientoService.lanzarAgente(agenteNombre));

        log.info("[Admin] lanzamiento de '{}' iniciado", agenteNombre);
        return ResponseEntity.accepted()
                .body(Map.of("ok", true, "agente", agenteNombre, "mensaje", "Notificaciones en curso"));
    }

    private boolean autenticado(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = decoded.split(":", 2);
            return parts.length == 2 && adminPassword.equals(parts[1]);
        } catch (Exception e) {
            return false;
        }
    }
}
