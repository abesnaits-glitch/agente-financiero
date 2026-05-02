package com.agentefinanciero.controller;

import com.agentefinanciero.service.ClaudeService;
import com.agentefinanciero.service.DashboardService;
import com.agentefinanciero.service.ReporteService;
import com.agentefinanciero.service.TwilioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
public class WhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppController.class);

    private static final String TWIML_EMPTY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response/>
            """;

    private final ClaudeService claudeService;
    private final TwilioService twilioService;
    private final DashboardService dashboardService;
    private final ReporteService reporteService;

    public WhatsAppController(ClaudeService claudeService,
                              TwilioService twilioService,
                              DashboardService dashboardService,
                              ReporteService reporteService) {
        this.claudeService   = claudeService;
        this.twilioService   = twilioService;
        this.dashboardService = dashboardService;
        this.reporteService  = reporteService;
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

        String usuarioId = from.replaceFirst("^whatsapp:\\+?", "");

        Thread.ofVirtual()
                .name("faro-reply-" + usuarioId)
                .start(() -> processAndReply(from, usuarioId, body));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(TWIML_EMPTY);
    }

    private void processAndReply(String from, String usuarioId, String body) {
        try {
            if (isReporteRequest(body)) {
                YearMonth mes = extraerMes(body);
                log.info("[WhatsApp] solicitud de reporte PDF para '{}' mes={}", usuarioId, mes);
                String pdfUrl = reporteService.generarReporte(usuarioId, mes);
                twilioService.sendWhatsAppWithMedia(from, "Tu reporte mensual en PDF:", pdfUrl);
            } else if (isDashboardRequest(body)) {
                YearMonth mes = extraerMes(body);
                log.info("[WhatsApp] solicitud de dashboard para '{}' mes={}", usuarioId, mes);
                String imageUrl = dashboardService.generarDashboard(usuarioId, mes);
                twilioService.sendWhatsAppWithMedia(from, "Tu resumen financiero:", imageUrl);
            } else {
                String respuesta = claudeService.chat(usuarioId, body);
                log.info("[WhatsApp] respondiendo a '{}': '{}'", usuarioId,
                        respuesta.length() > 100 ? respuesta.substring(0, 100) + "..." : respuesta);
                twilioService.sendWhatsApp(from, respuesta);
            }
        } catch (Exception e) {
            log.error("[WhatsApp] error procesando mensaje de '{}': {}", usuarioId, e.getMessage(), e);
            twilioService.sendWhatsApp(from, "Tuve un problema procesando tu solicitud. Intenta de nuevo 🙏");
        }
    }

    private static boolean isReporteRequest(String body) {
        String m = body.toLowerCase();
        return m.contains("reporte")
                || m.contains("pdf")
                || m.contains("estado de cuenta")
                || m.contains("informe")
                || m.contains("resumen pdf")
                || m.contains("enviame el reporte")
                || m.contains("envíame el reporte")
                || m.contains("quiero mi reporte")
                || m.contains("dame el reporte")
                || m.contains("genera el reporte")
                || m.contains("mi reporte");
    }

    static YearMonth extraerMes(String body) {
        String m = body.toLowerCase();

        if (m.contains("mes pasado") || m.contains("mes anterior")) {
            return YearMonth.now().minusMonths(1);
        }

        String[] nombres = {
            "enero","febrero","marzo","abril","mayo","junio",
            "julio","agosto","septiembre","octubre","noviembre","diciembre"
        };
        for (int i = 0; i < nombres.length; i++) {
            if (m.contains(nombres[i])) {
                YearMonth ym = YearMonth.of(YearMonth.now().getYear(), i + 1);
                // Si el mes mencionado es posterior al actual, asumir el año anterior
                if (ym.isAfter(YearMonth.now())) {
                    ym = ym.minusYears(1);
                }
                return ym;
            }
        }

        return YearMonth.now();
    }

    private static boolean isDashboardRequest(String body) {
        String m = body.toLowerCase();
        return m.contains("dashboard")
                || m.contains("resumen visual")
                || m.contains("grafico")
                || m.contains("gráfico")
                || m.contains("ver mis gastos")
                || m.contains("visual");
    }
}
