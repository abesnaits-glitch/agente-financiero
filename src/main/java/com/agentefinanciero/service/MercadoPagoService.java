package com.agentefinanciero.service;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.SuscripcionRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        log.info("[MP] creando preferencia para número={} email={}", numero, email);

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
        sus.setMpSubscriptionId(preference.getId());
        sus.setEstado("PENDIENTE");
        sus.setCreatedAt(LocalDateTime.now());
        suscripcionRepository.save(sus);

        return preference.getInitPoint();
    }

    public void procesarWebhook(MpWebhookBody body) throws Exception {
        if (body == null || body.getData() == null) return;
        if (!"payment".equals(body.getType())) return;

        String paymentId = body.getData().getId();
        log.info("[MP] webhook recibido type={} paymentId={}", body.getType(), paymentId);

        Payment payment;
        try {
            payment = new PaymentClient().get(Long.parseLong(paymentId));
            log.info("[MP] pago consultado status={} externalRef={}", payment.getStatus(), payment.getExternalReference());
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
            log.warn("[MP] no se encontró suscripción local para número={}", numero);
            return;
        }

        Suscripcion sus = opt.get();
        if ("ACTIVO".equals(sus.getEstado())) {
            log.info("[MP] número {} ya está activo, ignorando", numero);
            return;
        }

        sus.setEstado("ACTIVO");
        sus.setActivatedAt(LocalDateTime.now());
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
            log.info("[MP] perfil creado para usuarioId={}", usuarioId);
        }
    }

    private void enviarBienvenida(String numero) {
        try {
            twilioService.sendWhatsApp(numero, MENSAJE_BIENVENIDA);
            log.info("[MP] mensaje de bienvenida enviado a número={}", numero);
        } catch (Exception e) {
            log.error("[MP] error enviando bienvenida a {}: {}", numero, e.getMessage(), e);
        }
    }

    // Normaliza a solo dígitos. Agrega código de Chile si es número local (9 dígitos).
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
