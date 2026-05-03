package com.agentefinanciero.controller;

import com.agentefinanciero.model.Comentario;
import com.agentefinanciero.repository.ComentarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
public class ComentarioController {

    @Value("${admin.password:changeme}")
    private String adminPassword;

    private final ComentarioRepository repo;

    public ComentarioController(ComentarioRepository repo) {
        this.repo = repo;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    @GetMapping("/api/comentarios")
    public List<Comentario> listarAprobados() {
        return repo.findByAprobadoTrueOrderByFechaDesc();
    }

    @PostMapping("/api/comentarios")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        String nombre     = sanitize((String) body.get("nombre"), 100);
        String email      = sanitize((String) body.get("email"),  200);
        String texto      = sanitize((String) body.get("comentario"), 1000);
        Object estrellasO = body.get("estrellas");

        if (nombre == null || nombre.isBlank() || texto == null || texto.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "nombre y comentario son obligatorios"));

        int estrellas;
        try {
            estrellas = Integer.parseInt(String.valueOf(estrellasO));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "estrellas inválidas"));
        }
        if (estrellas < 1 || estrellas > 5)
            return ResponseEntity.badRequest().body(Map.of("error", "estrellas debe ser 1-5"));

        Comentario c = new Comentario();
        c.setNombre(nombre);
        c.setEmail(email);
        c.setEstrellas(estrellas);
        c.setComentario(texto);
        c.setFecha(LocalDate.now());
        c.setAprobado(true);

        repo.save(c);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Admin API (Basic Auth) ─────────────────────────────────────────────────

    @GetMapping(value = "/admin/comentarios", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> adminPanel(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Faro Admin\"")
                    .body("Acceso denegado");

        List<Comentario> todos = repo.findAllByOrderByFechaDesc();
        StringBuilder rows = new StringBuilder();
        for (Comentario c : todos) {
            String bg = Boolean.TRUE.equals(c.getAprobado()) ? "#1e2029" : "#2a1a1a";
            rows.append(String.format("""
                <tr style="background:%s">
                  <td>%d</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td>
                    <button onclick="toggleAprobar(%d,'%s')" class="btn-sm %s">%s</button>
                    <button onclick="eliminar(%d)" class="btn-sm btn-del">Eliminar</button>
                  </td>
                </tr>
                """,
                bg,
                c.getId(), esc(c.getNombre()), esc(c.getEmail() != null ? c.getEmail() : ""),
                c.getEstrellas(), esc(c.getComentario()), c.getFecha(),
                c.getId(), auth,
                Boolean.TRUE.equals(c.getAprobado()) ? "btn-ok" : "btn-warn",
                Boolean.TRUE.equals(c.getAprobado()) ? "✅ Aprobado" : "⛔ Oculto",
                c.getId()));
        }

        String html = """
            <!DOCTYPE html><html lang="es"><head>
            <meta charset="UTF-8"><title>Admin · Comentarios Faro</title>
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              body { background: #0f1117; color: #e2e2e2; font-family: system-ui, sans-serif; padding: 32px; }
              h1 { color: #00e5a0; margin-bottom: 24px; }
              table { width: 100%%; border-collapse: collapse; font-size: .85rem; }
              th, td { padding: 10px 12px; border-bottom: 1px solid #2a2d3a; text-align: left; vertical-align: top; }
              th { color: #888; font-weight: 600; }
              td:nth-child(4) { max-width: 300px; word-break: break-word; }
              .btn-sm { padding: 4px 10px; border-radius: 6px; border: none; cursor: pointer;
                        font-size: .78rem; font-weight: 600; margin-right: 4px; }
              .btn-ok  { background: #00e5a030; color: #00e5a0; }
              .btn-warn { background: #ff4d6d30; color: #ff4d6d; }
              .btn-del { background: #ff4d6d20; color: #ff4d6d; }
            </style>
            </head><body>
            <h1>Comentarios Faro (%d total)</h1>
            <table>
              <thead><tr>
                <th>#</th><th>Nombre</th><th>Email</th><th>Comentario</th>
                <th>⭐</th><th>Fecha</th><th>Acciones</th>
              </tr></thead>
              <tbody>%s</tbody>
            </table>
            <script>
              const AUTH = '%s';
              async function toggleAprobar(id, auth) {
                await fetch('/admin/comentarios/' + id + '/aprobar', {
                  method: 'PATCH', headers: { 'Authorization': auth }
                });
                location.reload();
              }
              async function eliminar(id) {
                if (!confirm('¿Eliminar este comentario?')) return;
                await fetch('/admin/comentarios/' + id, {
                  method: 'DELETE', headers: { 'Authorization': '%s' }
                });
                location.reload();
              }
            </script>
            </body></html>
            """.formatted(todos.size(), rows.toString(), auth, auth);

        return ResponseEntity.ok(html);
    }

    @PatchMapping("/admin/comentarios/{id}/aprobar")
    public ResponseEntity<?> toggleAprobar(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        return repo.findById(id).map(c -> {
            c.setAprobado(!Boolean.TRUE.equals(c.getAprobado()));
            repo.save(c);
            return ResponseEntity.ok(Map.of("aprobado", c.getAprobado()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/comentarios/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (!repo.existsById(id))
            return ResponseEntity.notFound().build();

        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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

    private static String sanitize(String s, int maxLen) {
        if (s == null) return null;
        s = s.strip();
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
