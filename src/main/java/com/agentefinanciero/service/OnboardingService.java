package com.agentefinanciero.service;

import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final UsuarioPerfilRepository repository;

    public OnboardingService(UsuarioPerfilRepository repository) {
        this.repository = repository;
    }

    // Returns true if the user should still be in the onboarding flow.
    // Null onboarding_paso means the user existed before this feature → skip onboarding.
    public boolean isEnOnboarding(String usuarioId) {
        return repository.findById(usuarioId)
                .map(p -> p.getOnboardingPaso() != null && p.getOnboardingPaso() < 6)
                .orElse(true); // user not in DB at all → start onboarding
    }

    // Processes the user's current message, advances the step, and returns the next message.
    public String procesarPaso(String usuarioId, String mensaje) {
        UsuarioPerfil perfil = repository.findById(usuarioId).orElseGet(() -> {
            UsuarioPerfil p = new UsuarioPerfil();
            p.setUsuarioId(usuarioId);
            p.setActualizadoEn(LocalDateTime.now());
            return p;
        });

        int paso = perfil.getOnboardingPaso() != null ? perfil.getOnboardingPaso() : 0;
        log.info("[Onboarding] usuario='{}' paso={} mensaje='{}'", usuarioId, paso, mensaje);

        return switch (paso) {
            case 0 -> {
                // First-ever message — ignore content, show greeting
                perfil.setOnboardingPaso(1);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                yield "¡Hola! Soy Faro, tu asistente financiero 👋 Primero, ¿cómo te llamas?";
            }
            case 1 -> {
                // Answer to "¿cómo te llamas?"
                String nombre = extraerNombre(mensaje);
                perfil.setNombre(nombre);
                perfil.setOnboardingPaso(2);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                yield "Mucho gusto " + nombre + ". ¿Cuánto ganas aproximadamente al mes? "
                        + "(esto me ayuda a darte mejores consejos)";
            }
            case 2 -> {
                // Answer to "¿cuánto ganas?"
                BigDecimal sueldo = parseMonto(mensaje);
                if (sueldo != null) {
                    perfil.setSueldoAproximado(sueldo);
                    // Pre-fill presupuesto at 80% of salary — user can override in step 5
                    perfil.setPresupuestoMensual(
                            sueldo.multiply(BigDecimal.valueOf(0.80)).setScale(0, RoundingMode.HALF_UP));
                }
                perfil.setOnboardingPaso(3);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                yield "¿Cuáles son tus gastos fijos mensuales? (arriendo, servicios, suscripciones)";
            }
            case 3 -> {
                // Answer about fixed expenses → save as notes
                perfil.setNotas("Gastos fijos: " + mensaje.trim());
                perfil.setOnboardingPaso(4);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                yield "¿Tienes alguna meta de ahorro o algo que quieras lograr financieramente?";
            }
            case 4 -> {
                // Answer about savings goal → save as metas
                perfil.setMetas(mensaje.trim());
                perfil.setOnboardingPaso(5);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                yield "Último: ¿cuánto te gustaría destinar como presupuesto mensual para "
                        + "gastos variables (comida, salidas, etc)?";
            }
            case 5 -> {
                // Answer about variable budget
                BigDecimal presup = parseMonto(mensaje);
                if (presup != null) {
                    perfil.setPresupuestoMensual(presup);
                }
                perfil.setOnboardingPaso(6);
                perfil.setActualizadoEn(LocalDateTime.now());
                repository.save(perfil);
                String nombre = perfil.getNombre() != null ? perfil.getNombre() : "amigo";
                yield "¡Listo " + nombre + "! Ya estamos calibrados 💪\n\n"
                        + "Esto es lo que puedo hacer por ti:\n\n"
                        + "📸 Mándame foto de tus boletas y las leo solo\n"
                        + "💬 Escríbeme tus gastos como te salgan: 'gasté 5k en café'\n"
                        + "📊 Pídeme 'reporte' y te genero un PDF con análisis\n"
                        + "🎯 Cuéntame tus metas y te ayudo a cumplirlas\n"
                        + "⚡ Te aviso cuando estés cerca del límite del presupuesto\n"
                        + "🔍 Detecto gastos pequeños que se te acumulan sin darte cuenta\n\n"
                        + "Cuéntame, ¿en qué te ayudo primero? O simplemente registra tu primer gasto.";
            }
            default -> "¡Listo! Ya completaste la configuración inicial. Cuéntame tus gastos cuando quieras.";
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String extraerNombre(String s) {
        if (s == null || s.isBlank()) return "amigo";
        String clean = s.toLowerCase(Locale.ROOT).trim()
                .replaceAll("^(me llamo|soy|mi nombre es|me dicen|llámame|llamame)\\s+", "")
                .trim();
        if (clean.isEmpty()) return capitalize(s.trim().split("\\s+")[0]);
        String[] parts = clean.split("\\s+");
        String nombre = capitalize(parts[0]);
        if (parts.length > 1 && parts[1].length() > 2) {
            nombre += " " + capitalize(parts[1]);
        }
        return nombre;
    }

    static BigDecimal parseMonto(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String s = texto.toLowerCase(Locale.ROOT).trim().replaceAll("[$\\s]", " ");

        // Millones
        Matcher mm = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*mill[oó]n(?:es)?").matcher(s);
        if (mm.find()) {
            double v = parseDouble(mm.group(1));
            return BigDecimal.valueOf(Math.round(v * 1_000_000));
        }

        // Miles (k or "mil")
        Matcher mk = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:k\\b|mil(?:es)?)").matcher(s);
        if (mk.find()) {
            double v = parseDouble(mk.group(1));
            return BigDecimal.valueOf(Math.round(v * 1_000));
        }

        // Number with dot-thousands separators like 500.000
        Matcher mdot = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})+)").matcher(s);
        if (mdot.find()) {
            long v = Long.parseLong(mdot.group(1).replace(".", ""));
            return BigDecimal.valueOf(v);
        }

        // Plain integer
        Matcher mnum = Pattern.compile("(\\d+)").matcher(s);
        if (mnum.find()) {
            long v = Long.parseLong(mnum.group(1));
            // If < 1000, assume thousands (Chilean convention: "500" = 500k)
            if (v < 1000) v *= 1000;
            return BigDecimal.valueOf(v);
        }

        return null;
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s.replace(",", "."));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
