package com.agentefinanciero.controller;

import com.agentefinanciero.service.ClaudeService;
import com.agentefinanciero.service.DashboardService;
import com.agentefinanciero.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);

    // TwiML vacío: Twilio acepta la respuesta sin enviar nada al usuario
    private static final String TWIML_EMPTY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response/>
            """;

    @Value("${app.base-url}")
    private String baseUrl;

    private final ClaudeService claudeService;
    private final TwilioService twilioService;
    private final DashboardService dashboardService;

    public WhatsAppController(ClaudeService claudeService,
                              TwilioService twilioService,
                              DashboardService dashboardService) {
        this.claudeService    = claudeService;
        this.twilioService    = twilioService;
        this.dashboardService = dashboardService;
    }

    @PostMapping(
            value = "/webhook/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> handleIncoming(
            @RequestParam("From") String from,
            @RequestParam(value = "Body", defaultValue = "") String body) {

        log.info("[WhatsApp] de='{}' mensaje='{}'", from, body);

        if (body.isBlank()) {
            log.warn("[WhatsApp] mensaje vacío de '{}'", from);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(TWIML_EMPTY);
        }

        // "whatsapp:+56912345678" → "56912345678" (ID interno del usuario)
        String usuarioId = from.replaceFirst("^whatsapp:\\+?", "");

        // Procesa en un virtual thread y devuelve TwiML vacío de inmediato
        // para evitar el timeout de 15 s de Twilio
        Thread.ofVirtual()
                .name("faro-reply-" + usuarioId)
                .start(() -> processAndReply(from, usuarioId, body));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(TWIML_EMPTY);
    }

    private void processAndReply(String from, String usuarioId, String body) {
        try {
            if (isDashboardRequest(body)) {
                log.info("[WhatsApp] solicitud de dashboard para '{}'", usuarioId);
                String dashboardUrl = baseUrl + "/dashboard/" + usuarioId;
                twilioService.sendWhatsApp(from, "Aquí tu dashboard 📊: " + dashboardUrl);
            } else {
                String respuesta = claudeService.chat(usuarioId, body);
                log.info("[WhatsApp] respondiendo a '{}': '{}'", usuarioId,
                        respuesta.length() > 100 ? respuesta.substring(0, 100) + "..." : respuesta);
                // 'from' conserva el prefijo "whatsapp:+..." que necesita la API de Twilio
                twilioService.sendWhatsApp(from, respuesta);
            }
        } catch (Exception e) {
            log.error("[WhatsApp] error procesando mensaje de '{}': {}", usuarioId, e.getMessage(), e);
            twilioService.sendWhatsApp(from, "Tuve un problema al generar tu dashboard. Intentá de nuevo 🙏");
        }
    }

    private static boolean isDashboardRequest(String body) {
        String m = body.toLowerCase();
        return m.contains("dashboard")
                || m.contains("resumen visual")
                || m.contains("gráfico")
                || m.contains("grafico")
                || m.contains("ver mis gastos")
                || m.contains("visual");
    }
}
