package com.agentefinanciero.controller;

import com.agentefinanciero.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping(value = "/dashboard/{usuarioId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getDashboard(@PathVariable String usuarioId) {
        log.info("[Dashboard] solicitud para usuario '{}'", usuarioId);
        String html = dashboardService.generarHtml(usuarioId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
