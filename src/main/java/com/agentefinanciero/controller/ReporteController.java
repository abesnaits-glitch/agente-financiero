package com.agentefinanciero.controller;

import com.agentefinanciero.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.Base64;

@RestController
public class ReporteController {

    @Value("${app.reports-dir:/tmp/faro-reports}")
    private String reportsDirPath;

    @Value("${admin.password}")
    private String adminPassword;

    private final TokenService tokenService;

    public ReporteController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // ── Token-based public endpoint (30-min, single-use) ─────────────────────

    @GetMapping("/reports/view")
    public ResponseEntity<Resource> serveReportByToken(
            @RequestParam(value = "t", required = false) String token) {

        String filename = tokenService.consumirToken(token);
        if (filename == null)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return serveFile(filename);
    }

    // ── Admin endpoint (Basic Auth) ───────────────────────────────────────────

    @GetMapping("/reports/{filename:.+}")
    public ResponseEntity<Resource> serveReportAdmin(
            @PathVariable String filename,
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (!autenticado(auth))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"Faro Admin\"")
                    .build();

        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();

        return serveFile(filename);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Resource> serveFile(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();
        Resource resource = new FileSystemResource(Paths.get(reportsDirPath).resolve(filename));
        if (!resource.exists() || !resource.isReadable())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(resource);
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
