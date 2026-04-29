package com.agentefinanciero.service;

import com.agentefinanciero.model.MpWebhookBody;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.SuscripcionRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreApprovalClient;
import com.mercadopago.client.preapproval.PreApprovalCreateRequest;
import com.mercadopago.resources.preapproval.PreApproval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Value("${app.base-url}")
    private String baseUrl;

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

    public String crearSuscripcion(String whatsappNumber) throws Exception {
        String numero = normalizarNumero(whatsappNumber);
        log.info("[MP] creando suscripción para número={}", numero);

        PreApprovalCreateRequest request = PreApprovalCreateRequest.builder()
                .reason("Faro ⚡ — Guía financiera personal")
                .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                        .frequency(1)
                        .frequencyType("months")
                        .transactionAmount(planAmount)
                        .currencyId(planCurrency)
                        .build())
                .backUrl(baseUrl + "/checkout?desde=mp")
                .build();

        PreApproval preApproval = new PreApprovalClient().create(request);
        log.info("[MP] suscripción creada id={} status={}", preApproval.getId(), preApproval.getStatus());

        Suscripcion sus = new Suscripcion();
        sus.setWhatsappNumber(numero);
        sus.setMpSubscriptionId(preApproval.getId());
        sus.setEstado("PENDIENTE");
        sus.setCreatedAt(LocalDateTime.now());
        suscripcionRepository.save(sus);

        return preApproval.getInitPoint();
    }

    public void procesarWebhook(MpWebhookBody body) throws Exception {
        if (body == null || body.getData() == null) return;
        if (!"subscription_preapproval".equals(body.getType())) return;

        String subscriptionId = body.getData().getId();
        log.info("[MP] webhook recibido type={} subscriptionId={}", body.getType(), subscriptionId);

        PreApproval subscription = new PreApprovalClient().get(subscriptionId);
        log.info("[MP] suscripción consultada status={}", subscription.getStatus());

        if (!"authorized".equals(subscription.getStatus())) return;

        Optional<Suscripcion> opt = suscripcionRepository.findByMpSubscriptionId(subscriptionId);
        if (opt.isEmpty()) {
            log.warn("[MP] no se encontró suscripción local para id={}", subscriptionId);
            return;
        }

        Suscripcion sus = opt.get();
        if ("ACTIVO".equals(sus.getEstado())) {
            log.info("[MP] suscripción {} ya está activa, ignorando", subscriptionId);
            return;
        }

        sus.setEstado("ACTIVO");
        sus.setActivatedAt(LocalDateTime.now());
        suscripcionRepository.save(sus);

        activarUsuario(sus.getWhatsappNumber());
        enviarBienvenida(sus.getWhatsappNumber());
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
