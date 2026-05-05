package com.agentefinanciero.service;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.SuscripcionCambio;
import com.agentefinanciero.repository.SuscripcionCambioRepository;
import com.agentefinanciero.repository.SuscripcionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MercadoPagoServiceTest {

    @Autowired private MercadoPagoService      service;
    @Autowired private SuscripcionRepository    susRepo;
    @Autowired private SuscripcionCambioRepository cambioRepo;

    // ── 1. Firma nula es rechazada ────────────────────────────────────────────

    @Test
    void firmaWebhook_nula_esRechazada() {
        assertThat(service.validarFirmaWebhook(null, null, null)).isFalse();
    }

    // ── 2. Firma con formato inválido es rechazada ────────────────────────────

    @Test
    void firmaWebhook_formatoInvalido_esRechazada() {
        assertThat(service.validarFirmaWebhook("not-a-valid-sig", "req-001", body("1"))).isFalse();
    }

    // ── 3. Firma HMAC correcta es aceptada ───────────────────────────────────

    @Test
    void firmaWebhook_hmacCorrecto_esAceptada() throws Exception {
        // test_mp_secret is set in src/test/resources/application.properties
        String secret    = "test_mp_secret";
        String ts        = "1715000000";
        String dataId    = "99";
        String requestId = "req-abc";
        String manifest  = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String v1 = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        String xSignature = "ts=" + ts + ",v1=" + v1;
        assertThat(service.validarFirmaWebhook(xSignature, requestId, body(dataId))).isTrue();
    }

    // ── 4. Idempotencia: mismo eventId no se procesa dos veces ───────────────

    @Test
    void procesarWebhook_mismoEventId_ignoraSegundaEntrega() throws Exception {
        // Suscripcion requerida para FK en suscripcion_cambios
        Suscripcion sus = new Suscripcion();
        sus.setWhatsappNumber("56900000001");
        sus.setAgente("faro");
        sus.setEstado("ACTIVO");
        sus.setCreatedAt(LocalDateTime.now());
        susRepo.save(sus);

        // Registro previo que simula un evento ya procesado
        SuscripcionCambio existing = new SuscripcionCambio();
        existing.setSuscripcionId(sus.getId());
        existing.setMpEventId("dup-event-001");
        existing.setMpEventType("payment");
        existing.setMpEventAction("payment.updated");
        existing.setEstadoAnterior("PENDIENTE");
        existing.setEstadoNuevo("ACTIVO");
        existing.setCreatedAt(LocalDateTime.now());
        cambioRepo.save(existing);

        // Webhook de tipo payment — sin idempotencia llamaría a PaymentClient (API externa)
        MpWebhookBody webhook = body("99999999");
        webhook.setType("payment");

        // Debe cortocircuitar antes de llamar a MP API (sin excepción)
        service.procesarWebhook(webhook, "dup-event-001");

        // Solo el registro pre-seeded debe existir
        assertThat(cambioRepo.findBySuscripcionIdOrderByCreatedAtDesc(sus.getId())).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MpWebhookBody body(String dataId) {
        MpWebhookBody b = new MpWebhookBody();
        b.setType("payment");
        MpWebhookBody.DataPayload d = new MpWebhookBody.DataPayload();
        d.setId(dataId);
        b.setData(d);
        return b;
    }
}
