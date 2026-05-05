package com.agentefinanciero.controller;

import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.SuscripcionCambio;
import com.agentefinanciero.repository.SuscripcionCambioRepository;
import com.agentefinanciero.repository.SuscripcionRepository;
import com.agentefinanciero.service.LanzamientoService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final Set<String> AGENTES_VALIDOS = Set.of("vita", "lilith", "nexo");
    private static final Set<String> ESTADOS_VALIDOS = Set.of("PENDIENTE", "ACTIVO", "CANCELADO", "VENCIDO", "TRIAL");

    @Value("${admin.password}")
    private String adminPassword;

    @PostConstruct
    void validarConfig() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD no configurada — arranque abortado");
        }
    }

    private final LanzamientoService          lanzamientoService;
    private final SuscripcionRepository       suscripcionRepository;
    private final SuscripcionCambioRepository cambioRepository;

    public AdminController(LanzamientoService lanzamientoService,
                           SuscripcionRepository suscripcionRepository,
                           SuscripcionCambioRepository cambioRepository) {
        this.lanzamientoService    = lanzamientoService;
        this.suscripcionRepository = suscripcionRepository;
        this.cambioRepository      = cambioRepository;
    }

    // ── Lanzar agente ─────────────────────────────────────────────────────────

    @PostMapping("/admin/lanzar-agente")
    public ResponseEntity<?> lanzarAgente(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {

        if (!autenticado(auth)) return unauthorized();

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

    // ── Listar suscripciones ──────────────────────────────────────────────────

    @GetMapping("/admin/suscripciones")
    public ResponseEntity<?> listarSuscripciones(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) String agente,
            @RequestParam(required = false) String estado) {

        if (!autenticado(auth)) return unauthorized();

        List<Suscripcion> lista;
        if (agente != null && estado != null) {
            lista = suscripcionRepository.findByAgenteAndEstadoOrderByCreatedAtDesc(agente, estado);
        } else if (agente != null) {
            lista = suscripcionRepository.findByAgenteOrderByCreatedAtDesc(agente);
        } else if (estado != null) {
            lista = suscripcionRepository.findByEstadoOrderByCreatedAtDesc(estado);
        } else {
            lista = suscripcionRepository.findAllByOrderByCreatedAtDesc();
        }

        log.info("[Admin] listando suscripciones agente={} estado={} total={}", agente, estado, lista.size());
        return ResponseEntity.ok(Map.of("total", lista.size(), "suscripciones", lista));
    }

    // ── Cambiar estado manualmente ────────────────────────────────────────────

    @PostMapping("/admin/suscripciones/{id}/cambiar-estado")
    public ResponseEntity<?> cambiarEstado(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        if (!autenticado(auth)) return unauthorized();

        String nuevoEstado = body.get("estado");
        String motivo      = body.getOrDefault("motivo", "cambio manual por admin");

        if (nuevoEstado == null || nuevoEstado.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "campo 'estado' requerido"));

        if (!ESTADOS_VALIDOS.contains(nuevoEstado))
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "estado inválido: " + nuevoEstado +
                            ". Valores permitidos: " + ESTADOS_VALIDOS));

        Optional<Suscripcion> opt = suscripcionRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "suscripción no encontrada: " + id));

        Suscripcion sus = opt.get();
        String anterior = sus.getEstado();

        sus.setEstado(nuevoEstado);
        suscripcionRepository.save(sus);

        SuscripcionCambio cambio = new SuscripcionCambio();
        cambio.setSuscripcionId(sus.getId());
        cambio.setMpEventId("admin-" + UUID.randomUUID());
        cambio.setMpEventType("admin");
        cambio.setMpEventAction("cambiar-estado");
        cambio.setEstadoAnterior(anterior);
        cambio.setEstadoNuevo(nuevoEstado);
        cambio.setDetalle(motivo);
        cambio.setCreatedAt(LocalDateTime.now());
        cambioRepository.save(cambio);

        log.info("[Admin] suscripcion {} {} → {} motivo='{}'", id, anterior, nuevoEstado, motivo);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "estadoAnterior", anterior,
                "estadoNuevo", nuevoEstado,
                "motivo", motivo));
    }

    // ── Historial de cambios ──────────────────────────────────────────────────

    @GetMapping("/admin/suscripciones/{id}/historial")
    public ResponseEntity<?> historialSuscripcion(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {

        if (!autenticado(auth)) return unauthorized();

        if (!suscripcionRepository.existsById(id))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "suscripción no encontrada: " + id));

        List<SuscripcionCambio> historial = cambioRepository.findBySuscripcionIdOrderByCreatedAtDesc(id);
        log.info("[Admin] historial suscripcion {} — {} registros", id, historial.size());
        return ResponseEntity.ok(Map.of(
                "suscripcionId", id,
                "total", historial.size(),
                "historial", historial));
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

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

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"Faro Admin\"")
                .body(Map.of("error", "no autorizado"));
    }
}
