package com.agentefinanciero.service;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.SuscripcionRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import com.agentefinanciero.util.LogUtil;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferencePaymentMethodsRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class MercadoPagoService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    private static final String MENSAJE_BIENVENIDA = """
            ¡Hola! Soy Faro ⚡, tu guía financiera personal.

            Estoy aquí para ayudarte a llevar tus finanzas sin complicaciones, directo por WhatsApp.

            Para empezar, cuéntame tu nombre y cuál es tu presupuesto mensual aproximado 💰

            Cuando quieras registrar un gasto, escríbeme algo como:
            • "Gasté $3.500 en almuerzo"
            • "Café $1.200"

            Y cuando quieras ver cómo vas, escribe *dashboard* o *resumen* 📊

            ¡Empecemos! ¿Cuál es tu nombre?
            """;

    private static final String SUCCESS_URL  = "https://agente-financiero-production.up.railway.app/checkout/success";
    private static final String CHECKOUT_URL = "https://agente-financiero-production.up.railway.app/checkout";
    private static final String WEBHOOK_URL  = "https://agente-financiero-production.up.railway.app/api/webhook/mercadopago";

    @Value("${mercadopago.plan.amount:5000}")
    private BigDecimal planAmount;

    @Value("${mercadopago.plan.currency:CLP}")
    private String planCurrency;

    // Empty default: validation is skipped gracefully until the env var is configured
    @Value("${mercadopago.webhook.secret:}")
    private String mpWebhookSecret;

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioPerfilRepository perfilRepository;
    private final TwilioService twilioService;

    public MercadoPagoService(SuscripcionRepository suscripcionRepository,
                               UsuarioPerfilRepository perfilRepository,
                               TwilioService twilioService) {
        this.suscripcionRepository = suscripcionRepository;
        this.perfilRepository      = perfilRepository;
        this.twilioService         = twilioService;
    }

    public String crearPreferencia(String whatsappNumber, String email) throws Exception {
        String numero = normalizarNumero(whatsappNumber);
        log.info("[MP] creando preferencia para número={} email={}", LogUtil.maskPhone(numero), LogUtil.maskEmail(email));

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(
                        PreferenceItemRequest.builder()
                                .title("Faro - Asistente financiero personal")
                                .quantity(1)
                                .currencyId(planCurrency)
                                .unitPrice(planAmount)
                                .build()
                ))
                .payer(PreferencePayerRequest.builder()
                        .email(email)
                        .build())
                .externalReference(numero)
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(SUCCESS_URL)
                        .failure(CHECKOUT_URL)
                        .pending(CHECKOUT_URL)
                        .build())
                .autoReturn("approved")
                .notificationUrl(WEBHOOK_URL)
                .paymentMethods(PreferencePaymentMethodsRequest.builder()
                        .installments(1)
                        .defaultInstallments(1)
                        .build())
                .statementDescriptor("FARO KIN")
                .build();

        Preference preference;
        try {
            preference = new PreferenceClient().create(request);
            log.info("[MP] preferencia creada id={}", preference.getId());
        } catch (MPApiException e) {
            log.error("[MP] Error HTTP {} al crear preferencia", e.getStatusCode());
            log.error("[MP] Response body: {}", e.getApiResponse().getContent());
            throw e;
        }

        Suscripcion sus = new Suscripcion();
        sus.setWhatsappNumber(numero);
        sus.setAgente("faro");
        sus.setMpSubscriptionId(preference.getId());
        sus.setEstado("PENDIENTE");
        sus.setCreatedAt(LocalDateTime.now());
        suscripcionRepository.save(sus);

        return preference.getInitPoint();
    }

    /**
     * Validates the x-signature header sent by MercadoPago on each webhook.
     * Format: ts=<epoch>,v1=<hmac_sha256_hex>
     * Signed manifest: id:<dataId>;request-id:<xRequestId>;ts:<ts>
     *
     * Returns true (and logs a warning) if MP_WEBHOOK_SECRET is not yet configured,
     * so existing deployments are not broken during the migration.
     */
    public boolean validarFirmaWebhook(String xSignature, String xRequestId, MpWebhookBody body) {
        if (mpWebhookSecret == null || mpWebhookSecret.isBlank()) {
            log.warn("[MP] MP_WEBHOOK_SECRET no configurado — validación de firma deshabilitada");
            return true;
        }
        if (xSignature == null || xSignature.isBlank()) {
            log.warn("[MP] webhook sin x-signature — rechazando");
            return false;
        }

        String ts = null, v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].strip();
                String val = kv[1].strip();
                if ("ts".equals(key))  ts = val;
                if ("v1".equals(key))  v1 = val;
            }
        }
        if (ts == null || v1 == null) {
            log.warn("[MP] formato x-signature inválido: '{}'", xSignature);
            return false;
        }

        String dataId    = (body != null && body.getData() != null) ? body.getData().getId() : "";
        String requestId = xRequestId != null ? xRequestId : "";
        String manifest  = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(mpWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
            boolean valid = computed.equals(v1);
            if (!valid) log.warn("[MP] firma inválida — manifest='{}' computed='{}...'", manifest, computed.substring(0, 8));
            return valid;
        } catch (Exception e) {
            log.error("[MP] error calculando HMAC: {}", e.getMessage());
            return false;
        }
    }

    public void procesarWebhook(MpWebhookBody body) throws Exception {
        if (body == null || body.getData() == null) return;
        if (!"payment".equals(body.getType())) return;

        String paymentId = body.getData().getId();
        log.info("[MP] webhook recibido type={} paymentId={}", body.getType(), paymentId);

        Payment payment;
        try {
            payment = new PaymentClient().get(Long.parseLong(paymentId));
            log.info("[MP] pago consultado status={} externalRef={}", payment.getStatus(),
                    LogUtil.maskPhone(payment.getExternalReference()));
        } catch (MPApiException e) {
            log.error("[MP] Error HTTP {} al consultar pago id={}", e.getStatusCode(), paymentId);
            log.error("[MP] Response body: {}", e.getApiResponse().getContent());
            throw e;
        }

        if (!"approved".equals(payment.getStatus())) return;

        String numero = payment.getExternalReference();
        if (numero == null || numero.isBlank()) {
            log.warn("[MP] pago {} sin external_reference, ignorando", paymentId);
            return;
        }

        Optional<Suscripcion> opt = suscripcionRepository.findByWhatsappNumber(numero);
        if (opt.isEmpty()) {
            log.warn("[MP] no se encontró suscripción local para número={}", LogUtil.maskPhone(numero));
            return;
        }

        Suscripcion sus = opt.get();
        if ("ACTIVO".equals(sus.getEstado())) {
            log.info("[MP] número {} ya está activo, ignorando", LogUtil.maskPhone(numero));
            return;
        }

        sus.setEstado("ACTIVO");
        sus.setActivatedAt(LocalDateTime.now());
        sus.setFechaInicio(LocalDateTime.now());
        if (sus.getAgente() == null) sus.setAgente("faro");
        suscripcionRepository.save(sus);

        activarUsuario(numero);
        enviarBienvenida(numero);
    }

    private void activarUsuario(String numero) {
        String usuarioId = numero.replaceAll("^\\+?", "");
        if (!perfilRepository.existsById(usuarioId)) {
            UsuarioPerfil perfil = new UsuarioPerfil();
            perfil.setUsuarioId(usuarioId);
            perfil.setActualizadoEn(LocalDateTime.now());
            perfilRepository.save(perfil);
            log.info("[MP] perfil creado para usuarioId={}", LogUtil.maskPhone(usuarioId));
        }
    }

    private void enviarBienvenida(String numero) {
        try {
            twilioService.sendWhatsApp(numero, MENSAJE_BIENVENIDA);
            log.info("[MP] bienvenida enviada a número={}", LogUtil.maskPhone(numero));
        } catch (Exception e) {
            log.error("[MP] error enviando bienvenida a {}: {}", LogUtil.maskPhone(numero), e.getMessage(), e);
        }
    }

    private String normalizarNumero(String input) {
        String digits = input.replaceAll("[^0-9]", "");
        if (digits.length() == 9 && digits.startsWith("9")) {
            return "56" + digits;
        }
        if (digits.length() == 10 && digits.startsWith("09")) {
            return "56" + digits.substring(1);
        }
        return digits;
    }
}
