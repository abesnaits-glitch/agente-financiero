package com.agentefinanciero.controller;

import com.agentefinanciero.service.BoletaService;
import com.agentefinanciero.service.ClaudeService;
import com.agentefinanciero.service.DashboardService;
import com.agentefinanciero.service.LogroService;
import com.agentefinanciero.service.OnboardingService;
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
import java.util.List;

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
    private final OnboardingService onboardingService;
    private final LogroService logroService;
    private final BoletaService boletaService;

    public WhatsAppController(ClaudeService claudeService,
                              TwilioService twilioService,
                              DashboardService dashboardService,
                              ReporteService reporteService,
                              OnboardingService onboardingService,
                              LogroService logroService,
                              BoletaService boletaService) {
        this.claudeService     = claudeService;
        this.twilioService     = twilioService;
        this.dashboardService  = dashboardService;
        this.reporteService    = reporteService;
        this.onboardingService = onboardingService;
        this.logroService      = logroService;
        this.boletaService     = boletaService;
    }

    @PostMapping(
            value = "/webhook/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> handleIncoming(
            @RequestParam("From") String from,
            @RequestParam(value = "Body", defaultValue = "") String body,
            @RequestParam(value = "NumMedia", defaultValue = "0") int numMedia,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl0,
            @RequestParam(value = "MediaContentType0", required = false) String mediaContentType0) {

        String usuarioId = from.replaceFirst("^whatsapp:\\+?", "");

        // Image received — route to boleta pipeline regardless of body text
        if (numMedia > 0 && mediaUrl0 != null && isImageContentType(mediaContentType0)) {
            log.info("[WhatsApp] imagen de='{}' url='{}' tipo='{}'", from, mediaUrl0, mediaContentType0);
            Thread.ofVirtual()
                    .name("faro-boleta-" + usuarioId)
                    .start(() -> boletaService.procesarYResponder(from, usuarioId, mediaUrl0, mediaContentType0));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(TWIML_EMPTY);
        }

        log.info("[WhatsApp] de='{}' mensaje='{}'", from, body);

        if (body.isBlank()) {
            log.warn("[WhatsApp] mensaje vacío de '{}'", from);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(TWIML_EMPTY);
        }

        Thread.ofVirtual()
                .name("faro-reply-" + usuarioId)
                .start(() -> processAndReply(from, usuarioId, body));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(TWIML_EMPTY);
    }

    private static boolean isImageContentType(String ct) {
        if (ct == null) return false;
        String t = ct.toLowerCase();
        return t.contains("image/jpeg") || t.contains("image/png")
            || t.contains("image/webp") || t.contains("image/gif");
    }

    private void processAndReply(String from, String usuarioId, String body) {
        try {
            // Onboarding takes priority over everything else
            if (onboardingService.isEnOnboarding(usuarioId)) {
                String resp = onboardingService.procesarPaso(usuarioId, body);
                log.info("[WhatsApp] onboarding para '{}': '{}'", usuarioId, resp);
                twilioService.sendWhatsApp(from, resp);
                return;
            }

            if (isLogrosRequest(body)) {
                twilioService.sendWhatsApp(from, logroService.buildLogrosMessage(usuarioId));
            } else if (isReporteRequest(body)) {
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

                // Check for newly unlocked achievements and notify proactively
                List<String> nuevosLogros = logroService.verificarLogros(usuarioId);
                for (String msg : nuevosLogros) {
                    if (msg != null && !msg.isBlank()) {
                        twilioService.sendWhatsApp(from, msg);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WhatsApp] error procesando mensaje de '{}': {}", usuarioId, e.getMessage(), e);
            twilioService.sendWhatsApp(from, "Tuve un problema procesando tu solicitud. Intenta de nuevo 🙏");
        }
    }

    private static boolean isLogrosRequest(String body) {
        String m = body.toLowerCase().trim();
        return m.equals("logros") || m.contains("mis logros") || m.contains("mis medallas")
                || m.contains("mis premios") || m.contains("ver logros") || m.contains("logros desbloqueados");
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
