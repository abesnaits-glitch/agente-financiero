package com.agentefinanciero.controller;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.service.MercadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    private final MercadoPagoService mercadoPagoService;

    public PagoController(MercadoPagoService mercadoPagoService) {
        this.mercadoPagoService = mercadoPagoService;
    }

    @PostMapping("/create-subscription")
    public ResponseEntity<Map<String, String>> crearSuscripcion(
            @RequestBody Map<String, String> body) {

        String whatsappNumber = body.get("whatsapp_number");
        String email          = body.get("email");

        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El número de WhatsApp es requerido"));
        }
        if (whatsappNumber.replaceAll("[^0-9]", "").length() > 15) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Número de WhatsApp inválido"));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El email es requerido"));
        }
        if (email.length() > 254) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email demasiado largo"));
        }

        try {
            String checkoutUrl = mercadoPagoService.crearPreferencia(whatsappNumber, email);
            return ResponseEntity.ok(Map.of("checkout_url", checkoutUrl));
        } catch (Exception e) {
            log.error("[Pago] error creando preferencia MP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo crear el enlace de pago. Intenta de nuevo."));
        }
    }

    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> recibirWebhook(
            @RequestHeader(value = "x-signature",  required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody(required = false) MpWebhookBody body) {

        // Always return 200 so MP does not retry, but skip processing on invalid signature
        if (!mercadoPagoService.validarFirmaWebhook(xSignature, xRequestId, body)) {
            log.warn("[Webhook MP] firma inválida — ignorando notificación");
            return ResponseEntity.ok().build();
        }

        try {
            mercadoPagoService.procesarWebhook(body, xRequestId);
        } catch (Exception e) {
            log.error("[Webhook MP] error procesando notificación: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
