package com.agentefinanciero.controller;

import com.agentefinanciero.service.ClaudeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);

    private static final String TWIML_ERROR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
              <Message>Lo siento, no pude procesar tu mensaje. Intenta de nuevo.</Message>
            </Response>
            """;

    private final ClaudeService claudeService;

    public WhatsAppController(ClaudeService claudeService) {
        this.claudeService = claudeService;
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
                    .body(TWIML_ERROR);
        }

        // "whatsapp:+56912345678" → "56912345678"
        String usuarioId = from.replaceFirst("^whatsapp:\\+?", "");

        try {
            String respuesta = claudeService.chat(usuarioId, body);
            String twiml = buildTwiml(respuesta);
            log.info("[WhatsApp] respondiendo a '{}': '{}'", usuarioId,
                    respuesta.length() > 100 ? respuesta.substring(0, 100) + "..." : respuesta);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(twiml);
        } catch (Exception e) {
            log.error("[WhatsApp] error procesando mensaje de '{}': {}", usuarioId, e.getMessage(), e);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(TWIML_ERROR);
        }
    }

    private String buildTwiml(String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                  <Message>%s</Message>
                </Response>
                """.formatted(escapeXml(message));
    }

    private String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
