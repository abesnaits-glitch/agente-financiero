package com.agentefinanciero.service;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.SuscripcionCambio;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.SuscripcionCambioRepository;
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
import com.mercadopago.client.preapproval.PreApprovalClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.mercadopago.resources.preapproval.PreApproval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private static final String MENSAJE_REEMBOLSO = """
            Tu reembolso fue procesado por MercadoPago. Tu acceso a Faro ha sido desactivado.
            Si tienes dudas, escríbenos a soporte@kinagentes.com
            """;

    private static final String MENSAJE_CONTRACARGO = """
            Detectamos una disputa (contracargo) en tu pago. Tu acceso a Faro fue suspendido.
            Si crees que es un error, contáctanos en soporte@kinagentes.com
            """;

    private static final String MENSAJE_CANCELACION = """
            Tu suscripción a Faro fue cancelada. Tu acceso ha sido desactivado.
            Cuando quieras volver, visita kinagentes.com ⭐
            """;

    private static final String MENSAJE_PAUSA = """
            Tu suscripción a Faro está pausada. Tu acceso está temporalmente inactivo.
            Reactívala cuando quieras desde el portal de MercadoPago.
            """;

    private static final String MENSAJE_REACTIVACION = """
            ¡Bienvenido de vuelta! 🎉 Tu suscripción a Faro está activa nuevamente.
            ¡Sigamos trabajando en tus finanzas! 💪
            """;

    private static final String SUCCESS_URL  = "https://agente-financiero-production.up.railway.app/checkout/success";
    private static final String CHECKOUT_URL = "https://agente-financiero-production.up.railway.app/checkout";
    private static final String WEBHOOK_URL  = "https://agente-financiero-production.up.railway.app/api/webhook/mercadopago";

    @Value("${mercadopago.plan.amount:5000}")
    private BigDecimal planAmount;

    @Value("${mercadopago.plan.currency:CLP}")
    private String planCurrency;

    @Value("${mercadopago.webhook.secret}")
    private String mpWebhookSecret;

    @PostConstruct
    void validarConfig() {
        if (mpWebhookSecret == null || mpWebhookSecret.isBlank()) {
            throw new IllegalStateException("MP_WEBHOOK_SECRET no configurado — arranque abortado");
        }
    }

    private final SuscripcionRepository       suscripcionRepository;
    private final SuscripcionCambioRepository  cambioRepository;
    private final UsuarioPerfilRepository      perfilRepository;
    private final TwilioService                twilioService;

    public MercadoPagoService(SuscripcionRepository suscripcionRepository,
                               SuscripcionCambioRepository cambioRepository,
                               UsuarioPerfilRepository perfilRepository,
                               TwilioService twilioService) {
        this.suscripcionRepository = suscripcionRepository;
        this.cambioRepository      = cambioRepository;
        this.perfilRepository      = perfilRepository;
        this.twilioService         = twilioService;
    }

    // ── Crear preferencia ─────────────────────────────────────────────────────

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

    // ── Validar firma ─────────────────────────────────────────────────────────

    /**
     * Validates the x-signature header sent by MercadoPago on each webhook.
     * Format: ts=<epoch>,v1=<hmac_sha256_hex>
     * Signed manifest: id:<dataId>;request-id:<xRequestId>;ts:<ts>
     */
    public boolean validarFirmaWebhook(String xSignature, String xRequestId, MpWebhookBody body) {
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
                if ("ts".equals(key)) ts = val;
                if ("v1".equals(key)) v1 = val;
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

    // ── Procesar webhook ──────────────────────────────────────────────────────

    public void procesarWebhook(MpWebhookBody body, String xRequestId) throws Exception {
        if (body == null || body.getData() == null) return;

        String type    = body.getType();
        String dataId  = body.getData().getId();
        String action  = body.getAction();
        // x-request-id is unique per MP delivery attempt — use as idempotency key
        String eventId = xRequestId != null ? xRequestId : UUID.randomUUID().toString();

        log.info("[MP] webhook type={} action={} dataId={} eventId={}", type, action, dataId, eventId);

        if (cambioRepository.existsByMpEventId(eventId)) {
            log.info("[MP] evento {} ya procesado — ignorando", eventId);
            return;
        }

        switch (type != null ? type : "") {
            case "payment"                  -> procesarPago(dataId, eventId, action);
            case "subscription_preapproval" -> procesarPreapproval(dataId, eventId, action);
            default                          -> log.info("[MP] tipo de evento ignorado: {}", type);
        }
    }

    private void procesarPago(String dataId, String eventId, String action) throws Exception {
        Payment payment;
        try {
            payment = new PaymentClient().get(Long.parseLong(dataId));
            log.info("[MP] pago id={} status={} externalRef={}",
                    dataId, payment.getStatus(), LogUtil.maskPhone(payment.getExternalReference()));
        } catch (MPApiException e) {
            log.error("[MP] Error HTTP {} consultando pago id={}", e.getStatusCode(), dataId);
            throw e;
        }

        String status = payment.getStatus();
        String numero = payment.getExternalReference();

        switch (status != null ? status : "") {
            case "approved"     -> activarPorPago(numero, dataId, eventId, action);
            case "refunded"     -> cancelarPorEvento(numero, eventId, "payment", action, MENSAJE_REEMBOLSO);
            case "charged_back" -> cancelarPorEvento(numero, eventId, "payment", action, MENSAJE_CONTRACARGO);
            default             -> log.info("[MP] pago {} status={} — sin acción", dataId, status);
        }
    }

    private void procesarPreapproval(String dataId, String eventId, String action) throws Exception {
        PreApproval preApproval;
        try {
            preApproval = new PreApprovalClient().get(dataId);
            log.info("[MP] preapproval id={} status={} externalRef={}",
                    dataId, preApproval.getStatus(), LogUtil.maskPhone(preApproval.getExternalReference()));
        } catch (MPApiException e) {
            log.error("[MP] Error HTTP {} consultando preapproval id={}", e.getStatusCode(), dataId);
            throw e;
        }

        String status = preApproval.getStatus();
        String numero = preApproval.getExternalReference();

        switch (status != null ? status : "") {
            case "cancelled"  -> cancelarPorEvento(numero, eventId, "subscription_preapproval", action, MENSAJE_CANCELACION);
            case "paused"     -> pausarPorEvento(numero, eventId, "subscription_preapproval", action);
            case "authorized" -> reactivarPorEvento(numero, eventId, "subscription_preapproval", action);
            default           -> log.info("[MP] preapproval {} status={} — sin acción", dataId, status);
        }
    }

    // ── Handlers de transición de estado ─────────────────────────────────────

    private void activarPorPago(String numero, String paymentId, String eventId, String action) {
        if (numero == null || numero.isBlank()) {
            log.warn("[MP] pago {} sin external_reference, ignorando", paymentId);
            return;
        }

        Optional<Suscripcion> opt = suscripcionRepository.findByWhatsappNumber(numero);
        if (opt.isEmpty()) {
            log.warn("[MP] no se encontró suscripción para número={}", LogUtil.maskPhone(numero));
            return;
        }

        Suscripcion sus = opt.get();
        if ("ACTIVO".equals(sus.getEstado())) {
            log.info("[MP] número {} ya está ACTIVO, ignorando", LogUtil.maskPhone(numero));
            registrarCambio(sus, eventId, "payment", action, "ACTIVO", "ACTIVO", "noop: ya activo");
            return;
        }

        String anterior = sus.getEstado();
        sus.setEstado("ACTIVO");
        sus.setActivatedAt(LocalDateTime.now());
        sus.setFechaInicio(LocalDateTime.now());
        if (sus.getAgente() == null) sus.setAgente("faro");
        suscripcionRepository.save(sus);

        registrarCambio(sus, eventId, "payment", action, anterior, "ACTIVO", null);
        activarUsuario(numero);
        enviarBienvenida(numero);
    }

    private void cancelarPorEvento(String numero, String eventId, String tipo,
                                    String action, String mensajeWA) {
        Optional<Suscripcion> opt = buscarPorNumero(numero, tipo + "/cancelar");
        if (opt.isEmpty()) return;

        Suscripcion sus = opt.get();
        boolean changed = cambiarEstado(sus, "CANCELADO", eventId, tipo, action, null);
        if (changed) enviarNotificacion(sus.getWhatsappNumber(), mensajeWA);
    }

    private void pausarPorEvento(String numero, String eventId, String tipo, String action) {
        Optional<Suscripcion> opt = buscarPorNumero(numero, tipo + "/pausar");
        if (opt.isEmpty()) return;

        Suscripcion sus = opt.get();
        boolean changed = cambiarEstado(sus, "VENCIDO", eventId, tipo, action, null);
        if (changed) enviarNotificacion(sus.getWhatsappNumber(), MENSAJE_PAUSA);
    }

    private void reactivarPorEvento(String numero, String eventId, String tipo, String action) {
        Optional<Suscripcion> opt = buscarPorNumero(numero, tipo + "/reactivar");
        if (opt.isEmpty()) return;

        Suscripcion sus = opt.get();
        boolean changed = cambiarEstado(sus, "ACTIVO", eventId, tipo, action, null);
        if (changed) {
            sus.setActivatedAt(LocalDateTime.now());
            suscripcionRepository.save(sus);
            enviarNotificacion(sus.getWhatsappNumber(), MENSAJE_REACTIVACION);
        }
    }

    // ── Helpers internos ─────────────────────────────────────────────────────

    private Optional<Suscripcion> buscarPorNumero(String numero, String contexto) {
        if (numero == null || numero.isBlank()) {
            log.warn("[MP] {} sin external_reference, ignorando", contexto);
            return Optional.empty();
        }
        Optional<Suscripcion> opt = suscripcionRepository.findByWhatsappNumber(numero);
        if (opt.isEmpty()) {
            log.warn("[MP] no se encontró suscripción para número={} contexto={}", LogUtil.maskPhone(numero), contexto);
        }
        return opt;
    }

    /**
     * Transitions sus to nuevoEstado, saves, and records the audit entry.
     * Returns true when the state actually changed (i.e. anterior != nuevo).
     */
    private boolean cambiarEstado(Suscripcion sus, String nuevoEstado, String eventId,
                                   String tipo, String action, String detalle) {
        String anterior = sus.getEstado();
        if (nuevoEstado.equals(anterior)) {
            log.info("[MP] suscripcion {} ya está en estado {}", sus.getId(), nuevoEstado);
            registrarCambio(sus, eventId, tipo, action, anterior, nuevoEstado, "noop: ya en " + nuevoEstado);
            return false;
        }
        sus.setEstado(nuevoEstado);
        suscripcionRepository.save(sus);
        log.info("[MP] suscripcion {} {} → {}", sus.getId(), anterior, nuevoEstado);
        registrarCambio(sus, eventId, tipo, action, anterior, nuevoEstado, detalle);
        return true;
    }

    private void registrarCambio(Suscripcion sus, String eventId, String tipo, String action,
                                  String anterior, String nuevo, String detalle) {
        try {
            SuscripcionCambio cambio = new SuscripcionCambio();
            cambio.setSuscripcionId(sus.getId());
            cambio.setMpEventId(eventId);
            cambio.setMpEventType(tipo);
            cambio.setMpEventAction(action);
            cambio.setEstadoAnterior(anterior);
            cambio.setEstadoNuevo(nuevo);
            cambio.setDetalle(detalle);
            cambio.setCreatedAt(LocalDateTime.now());
            cambioRepository.save(cambio);
        } catch (Exception e) {
            // Audit failure must not break the main webhook flow
            log.error("[MP] error guardando cambio de suscripcion {}: {}", sus.getId(), e.getMessage());
        }
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

    private void enviarNotificacion(String numero, String mensaje) {
        try {
            twilioService.sendWhatsApp(numero, mensaje);
            log.info("[MP] notificación enviada a número={}", LogUtil.maskPhone(numero));
        } catch (Exception e) {
            log.error("[MP] error enviando notificación a {}: {}", LogUtil.maskPhone(numero), e.getMessage(), e);
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
