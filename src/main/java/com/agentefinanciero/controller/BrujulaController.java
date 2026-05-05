package com.agentefinanciero.controller;

import com.agentefinanciero.model.BrujulaAnalisis;
import com.agentefinanciero.model.BrujulaProyecto;
import com.agentefinanciero.model.BrujulaRequest;
import com.agentefinanciero.repository.BrujulaAnalisisRepository;
import com.agentefinanciero.repository.BrujulaProyectoRepository;
import com.agentefinanciero.service.BrujulaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class BrujulaController {

    private static final Logger log = LoggerFactory.getLogger(BrujulaController.class);

    private final BrujulaService              brujulaService;
    private final BrujulaAnalisisRepository   analisisRepo;
    private final BrujulaProyectoRepository   proyectoRepo;
    private final ObjectMapper                objectMapper = new ObjectMapper();

    public BrujulaController(BrujulaService            brujulaService,
                             BrujulaAnalisisRepository  analisisRepo,
                             BrujulaProyectoRepository  proyectoRepo) {
        this.brujulaService = brujulaService;
        this.analisisRepo   = analisisRepo;
        this.proyectoRepo   = proyectoRepo;
    }

    // ── Páginas HTML (forward a static resources) ─────────────────────────────

    @GetMapping("/brujula")
    public String brujulaHome() {
        return "forward:/brujula/index.html";
    }

    @GetMapping("/brujula/evaluar")
    public String brujulaEvaluar() {
        return "forward:/brujula/evaluar.html";
    }

    @GetMapping("/brujula/resultados")
    public String brujulaResultados() {
        return "forward:/brujula/resultados.html";
    }

    // ── API: Análisis ─────────────────────────────────────────────────────────

    @PostMapping("/api/brujula/analizar")
    @ResponseBody
    public ResponseEntity<?> analizar(@RequestBody BrujulaRequest request) {
        log.info("[Brújula] POST /api/brujula/analizar tipo='{}'", request.getTipoEvaluacion());

        if (request.getTipoEvaluacion() == null || request.getTipoEvaluacion().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo tipoEvaluacion es requerido"));
        }
        if (request.getEmail() == null && request.getTelefono() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Se requiere al menos email o teléfono para identificar al usuario"));
        }
        if (request.getEmail() != null && request.getEmail().length() > 254) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email demasiado largo"));
        }

        String plan = request.getPlan() != null ? request.getPlan() : "free";
        String usuarioId = resolverUsuarioId(request);

        if (!brujulaService.puedeAnalizar(usuarioId, plan)) {
            log.info("[Brújula] cuota agotada para usuario='{}' plan='{}'", maskId(usuarioId), plan);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Alcanzaste el límite de análisis para este mes con el plan " + plan,
                            "upgrade_url", "/brujula#precios"
                    ));
        }

        try {
            BrujulaAnalisis analisis = brujulaService.generarAnalisis(request);
            return ResponseEntity.ok(analisisToResponse(analisis));
        } catch (Exception e) {
            log.error("[Brújula] error en /analizar: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo generar el análisis. Por favor intenta de nuevo."));
        }
    }

    @GetMapping("/api/brujula/analisis/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerAnalisis(@PathVariable Long id) {
        log.info("[Brújula] GET /api/brujula/analisis/{}", id);

        return analisisRepo.findById(id)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(analisisToResponse(a)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Análisis no encontrado")));
    }

    @GetMapping("/api/brujula/analisis/{id}/pdf")
    @ResponseBody
    public ResponseEntity<?> descargarPdf(@PathVariable Long id,
                                          @RequestParam(value = "plan", defaultValue = "free") String plan) {
        log.info("[Brújula] GET /api/brujula/analisis/{}/pdf plan='{}'", id, plan);

        if ("free".equalsIgnoreCase(plan)) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of(
                            "error", "El PDF está disponible en el plan Esencial o superior",
                            "upgrade_url", "/brujula#precios"
                    ));
        }

        if (!analisisRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Análisis no encontrado"));
        }

        try {
            byte[] pdf = brujulaService.generarPdf(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "brujula-analisis-" + id + ".pdf");
            headers.setContentLength(pdf.length);
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("[Brújula] error generando PDF id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo generar el PDF. Por favor intenta de nuevo."));
        }
    }

    // ── API: Proyectos ────────────────────────────────────────────────────────

    @PostMapping("/api/brujula/proyectos")
    @ResponseBody
    public ResponseEntity<?> iniciarProyecto(@RequestBody Map<String, Object> body) {
        Object analisisIdObj = body.get("analisisId");
        Object usuarioIdObj  = body.get("usuarioId");

        if (analisisIdObj == null || usuarioIdObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Se requieren los campos analisisId y usuarioId"));
        }

        Long analisisId;
        try {
            analisisId = Long.valueOf(analisisIdObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "analisisId debe ser un número"));
        }

        String usuarioId = usuarioIdObj.toString().trim();
        if (usuarioId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "usuarioId no puede estar vacío"));
        }

        log.info("[Brújula] POST /api/brujula/proyectos analisisId={}", analisisId);

        try {
            BrujulaProyecto proyecto = brujulaService.iniciarSeguimiento(analisisId, usuarioId);
            return ResponseEntity.ok(proyectoToResponse(proyecto));
        } catch (Exception e) {
            log.error("[Brújula] error iniciando proyecto analisisId={}: {}", analisisId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo iniciar el seguimiento. Por favor intenta de nuevo."));
        }
    }

    @GetMapping("/api/brujula/proyectos/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerProyecto(@PathVariable Long id) {
        log.info("[Brújula] GET /api/brujula/proyectos/{}", id);

        return proyectoRepo.findById(id)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(proyectoToResponse(p)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Proyecto no encontrado")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> analisisToResponse(BrujulaAnalisis a) {
        Object outputParsed = null;
        if (a.getOutputData() != null) {
            try {
                outputParsed = objectMapper.readValue(a.getOutputData(), Object.class);
            } catch (Exception e) {
                log.warn("[Brújula] no se pudo parsear output_data para analisis id={}", a.getId());
                outputParsed = a.getOutputData();
            }
        }
        return Map.of(
                "id",              a.getId(),
                "estado",          a.getEstado(),
                "tipoEvaluacion",  a.getTipoEvaluacion() != null ? a.getTipoEvaluacion() : "",
                "veredicto",       a.getVeredicto()      != null ? a.getVeredicto()      : "",
                "plan",            a.getPlan()           != null ? a.getPlan()           : "free",
                "createdAt",       a.getCreatedAt()      != null ? a.getCreatedAt().toString() : "",
                "analisis",        outputParsed != null ? outputParsed : Map.of()
        );
    }

    private Map<String, Object> proyectoToResponse(BrujulaProyecto p) {
        return Map.of(
                "id",              p.getId(),
                "analisisId",      p.getAnalisisId(),
                "nombreProyecto",  p.getNombreProyecto() != null ? p.getNombreProyecto() : "",
                "estado",          p.getEstado()         != null ? p.getEstado()         : "",
                "proximoCheckin",  p.getProximoCheckin() != null ? p.getProximoCheckin().toString() : "",
                "ultimoCheckin",   p.getUltimoCheckin()  != null ? p.getUltimoCheckin().toString()  : "",
                "createdAt",       p.getCreatedAt()      != null ? p.getCreatedAt().toString() : ""
        );
    }

    private static String resolverUsuarioId(BrujulaRequest request) {
        if (request.getTelefono() != null && !request.getTelefono().isBlank()) {
            return request.getTelefono().replaceAll("[^0-9]", "");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return request.getEmail().toLowerCase();
        }
        return "anon";
    }

    private static String maskId(String id) {
        if (id == null || id.length() <= 4) return "***";
        return "*".repeat(id.length() - 4) + id.substring(id.length() - 4);
    }
}
