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
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El número de WhatsApp es requerido"));
        }

        try {
            String checkoutUrl = mercadoPagoService.crearSuscripcion(whatsappNumber);
            return ResponseEntity.ok(Map.of("checkout_url", checkoutUrl));
        } catch (Exception e) {
            log.error("[Pago] error creando suscripción MP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo crear el enlace de pago. Intenta de nuevo."));
        }
    }

    @PostMapping("/webhook/mercadopago")
    public ResponseEntity<Void> recibirWebhook(@RequestBody(required = false) MpWebhookBody body) {
        // Siempre responder 200 para que MP no reintente indefinidamente
        try {
            mercadoPagoService.procesarWebhook(body);
        } catch (Exception e) {
            log.error("[Webhook MP] error procesando notificación: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
