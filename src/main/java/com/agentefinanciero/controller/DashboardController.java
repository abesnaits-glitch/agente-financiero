package com.agentefinanciero.controller;

import com.agentefinanciero.service.DashboardService;
import com.agentefinanciero.service.TokenService;
import com.agentefinanciero.util.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Base64;

@RestController
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @Value("${admin.password}")
    private String adminPassword;

    private final DashboardService dashboardService;
    private final TokenService tokenService;

    public DashboardController(DashboardService dashboardService, TokenService tokenService) {
        this.dashboardService = dashboardService;
        this.tokenService     = tokenService;
    }

    // ── Token-based public endpoint ───────────────────────────────────────────

    @GetMapping(value = "/dashboard/view", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDashboardByToken(
            @RequestParam(value = "t", required = false) String token) {

        String tokenValue = tokenService.consumirToken(token);
        if (tokenValue == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml("Enlace expirado o inválido",
                            "Solicita uno nuevo escribiéndole a Faro por WhatsApp."));
        }

        String[] parts    = tokenValue.split("\\|", 2);
        String usuarioId  = parts[0];
        YearMonth mes     = parts.length > 1 ? YearMonth.parse(parts[1]) : YearMonth.now();

        log.info("[Dashboard] acceso por token usuario='{}'", LogUtil.maskPhone(usuarioId));
        String html = dashboardService.generarHtml(usuarioId, mes);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    // ── Admin endpoint (Basic Auth) ───────────────────────────────────────────

    @GetMapping(value = "/dashboard/{usuarioId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDashboardAdmin(
            @PathVariable String usuarioId,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Faro Admin\"")
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorHtml("Acceso denegado", "Se requiere autenticación de administrador."));

        log.info("[Dashboard] acceso admin usuario='{}'", LogUtil.maskPhone(usuarioId));
        String html = dashboardService.generarHtml(usuarioId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private static String errorHtml(String title, String message) {
        return """
                <!DOCTYPE html><html lang="es"><head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Faro</title>
                <style>
                  body{background:#161820;color:#ccc;font-family:sans-serif;
                    display:flex;justify-content:center;align-items:center;
                    min-height:100vh;text-align:center;padding:24px;}
                  h2{color:#ff4d6d;margin-bottom:12px;}
                  p{color:#888;max-width:320px;}
                </style>
                </head><body>
                  <div><h2>%s</h2><p>%s</p></div>
                </body></html>
                """.formatted(title, message);
    }
}
