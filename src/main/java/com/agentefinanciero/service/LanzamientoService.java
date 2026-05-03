package com.agentefinanciero.service;

import com.agentefinanciero.model.AgenteLanzado;
import com.agentefinanciero.model.CrossNotificacion;
import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.AgenteLanzadoRepository;
import com.agentefinanciero.repository.CrossNotificacionRepository;
import com.agentefinanciero.repository.SuscripcionRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LanzamientoService {

    private static final Logger log = LoggerFactory.getLogger(LanzamientoService.class);

    private final AgenteLanzadoRepository agenteLanzadoRepo;
    private final CrossNotificacionRepository crossRepo;
    private final SuscripcionRepository suscripcionRepo;
    private final UsuarioPerfilRepository perfilRepo;
    private final TwilioService twilioService;

    public LanzamientoService(AgenteLanzadoRepository agenteLanzadoRepo,
                               CrossNotificacionRepository crossRepo,
                               SuscripcionRepository suscripcionRepo,
                               UsuarioPerfilRepository perfilRepo,
                               TwilioService twilioService) {
        this.agenteLanzadoRepo = agenteLanzadoRepo;
        this.crossRepo         = crossRepo;
        this.suscripcionRepo   = suscripcionRepo;
        this.perfilRepo        = perfilRepo;
        this.twilioService     = twilioService;
    }

    public void lanzarAgente(String agente) {
        // Mark agent as active
        AgenteLanzado al = agenteLanzadoRepo.findByNombre(agente).orElseGet(() -> {
            AgenteLanzado nuevo = new AgenteLanzado();
            nuevo.setNombre(agente);
            return nuevo;
        });
        al.setActivo(true);
        al.setFechaLanzamiento(LocalDateTime.now());
        agenteLanzadoRepo.save(al);
        log.info("[Lanzamiento] agente '{}' marcado activo", agente);

        // Find eligible Faro users: active subscription, registered 7+ days ago
        LocalDateTime limite = LocalDateTime.now().minusDays(7);
        List<Suscripcion> candidatos = suscripcionRepo
                .findByAgenteAndEstadoAndCreatedAtBefore("faro", "ACTIVO", limite);
        log.info("[Lanzamiento] {} candidatos para notificación de '{}'", candidatos.size(), agente);

        int enviados = 0;
        int omitidos = 0;

        for (Suscripcion sus : candidatos) {
            String usuarioId = sus.getWhatsappNumber();

            if (crossRepo.existsByUsuarioIdAndAgenteDestino(usuarioId, agente)) {
                omitidos++;
                continue;
            }

            String nombre = resolverNombre(usuarioId);
            String mensaje = buildLaunchMessage(agente, nombre);
            String whatsappDest = "whatsapp:+" + usuarioId;

            try {
                twilioService.sendWhatsApp(whatsappDest, mensaje);

                CrossNotificacion cn = new CrossNotificacion();
                cn.setUsuarioId(usuarioId);
                cn.setAgenteOrigen("faro");
                cn.setAgenteDestino(agente);
                cn.setEnviado(LocalDateTime.now());
                crossRepo.save(cn);

                enviados++;
                log.info("[Lanzamiento] notificado usuarioId={} agente={}", usuarioId, agente);
            } catch (Exception e) {
                log.error("[Lanzamiento] error enviando a usuarioId={}: {}", usuarioId, e.getMessage());
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[Lanzamiento] interrumpido tras {} envíos", enviados);
                break;
            }
        }

        log.info("[Lanzamiento] completado agente='{}' enviados={} omitidos={}", agente, enviados, omitidos);
    }

    private String resolverNombre(String usuarioId) {
        return perfilRepo.findById(usuarioId)
                .map(UsuarioPerfil::getNombre)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    private String buildLaunchMessage(String agente, String nombre) {
        String saludo = (nombre != null && !nombre.isBlank())
                ? "Hola " + nombre + " 👋"
                : "Hola 👋";

        return switch (agente.toLowerCase()) {
            case "lilith" -> saludo + " Te tengo una buena noticia.\n\n"
                    + "Mi hermana Lilith ya está disponible 🌙\n\n"
                    + "Es la organizadora personal de la familia Kin:\n"
                    + "📅 Agenda reuniones y eventos\n"
                    + "✅ Organiza tareas con matriz Eisenhower\n"
                    + "💡 Captura ideas al vuelo\n"
                    + "⏰ Recordatorios inteligentes\n\n"
                    + "Si vives corriendo entre pendientes, te va a cambiar el día.\n\n"
                    + "Pruébala 👉 https://kinagentes.com/contratar/lilith\n\n"
                    + "Sigo cuidándote la plata por aquí 💪\n"
                    + "- Faro";
            case "vita" -> saludo + " Te tengo una buena noticia.\n\n"
                    + "Mi hermana Vita ya está disponible 🌿\n\n"
                    + "Es la coach de salud de la familia Kin:\n"
                    + "🥗 Hábitos de alimentación y bienestar\n"
                    + "🏃 Rutinas de ejercicio personalizadas\n"
                    + "💧 Seguimiento de hidratación y sueño\n"
                    + "📈 Registro de progreso semana a semana\n\n"
                    + "Si quieres cuidar tu cuerpo tanto como cuidas tu plata, esta es tu oportunidad.\n\n"
                    + "Pruébala 👉 https://kinagentes.com/contratar/vita\n\n"
                    + "Sigo cuidándote la plata por aquí 💪\n"
                    + "- Faro";
            case "nexo" -> saludo + " Te tengo una buena noticia.\n\n"
                    + "Mi hermano Nexo ya está disponible 🔗\n\n"
                    + "Es el conector profesional de la familia Kin:\n"
                    + "🤝 Gestiona tus contactos y redes\n"
                    + "📬 Seguimiento de conversaciones pendientes\n"
                    + "🎯 Te recuerda a quién contactar y cuándo\n"
                    + "💼 Organiza tu pipeline de oportunidades\n\n"
                    + "Si tu red es tu activo más valioso, Nexo te ayuda a trabajarla.\n\n"
                    + "Pruébalo 👉 https://kinagentes.com/contratar/nexo\n\n"
                    + "Sigo cuidándote la plata por aquí 💪\n"
                    + "- Faro";
            default -> saludo + " Un nuevo agente de la familia Kin está disponible: " + agente + ".\n\n"
                    + "Visita 👉 https://kinagentes.com para conocerlo.\n\n"
                    + "- Faro";
        };
    }
}
