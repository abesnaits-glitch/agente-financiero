package com.agentefinanciero.service;

import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.GastoRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class RecordatoriosScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatoriosScheduler.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final GastoService gastoService;
    private final GastoRepository gastoRepository;
    private final UsuarioPerfilRepository perfilRepository;
    private final TwilioService twilioService;

    public RecordatoriosScheduler(GastoService gastoService,
                                  GastoRepository gastoRepository,
                                  UsuarioPerfilRepository perfilRepository,
                                  TwilioService twilioService) {
        this.gastoService     = gastoService;
        this.gastoRepository  = gastoRepository;
        this.perfilRepository = perfilRepository;
        this.twilioService    = twilioService;
    }

    // ── 1. Inactivity reminder — daily at 10:00 AM Chile ─────────────────────
    @Scheduled(cron = "0 0 10 * * *", zone = "America/Santiago")
    public void checkInactividad() {
        LocalDate hoy    = LocalDate.now(CHILE);
        LocalDate limite = hoy.minusDays(3);

        List<UsuarioPerfil> perfiles = perfilRepository.findAll();
        log.info("[Recordatorio] checkInactividad: revisando {} perfiles", perfiles.size());

        for (UsuarioPerfil perfil : perfiles) {
            String uid = perfil.getUsuarioId();
            try {
                // Skip if already reminded within the last 7 days
                if (perfil.getUltimoRecordatorioInactividad() != null
                        && perfil.getUltimoRecordatorioInactividad().isAfter(hoy.minusDays(7))) {
                    continue;
                }

                Optional<LocalDate> ultimaActividad = gastoRepository.findUltimaFechaActividad(uid);

                // Only notify users who had prior activity but have been silent for ≥3 days
                if (ultimaActividad.isEmpty()) continue;
                if (!ultimaActividad.get().isBefore(limite)) continue;

                long dias   = ChronoUnit.DAYS.between(ultimaActividad.get(), hoy);
                String nombre = perfil.getNombre() != null ? perfil.getNombre() : "amigo";

                String msg = "Hola " + nombre + ", llevas " + dias + " días sin registrar movimientos. "
                        + "¿Todo bien con tus finanzas? Recuerda que cada gasto cuenta 😊";

                twilioService.sendWhatsApp(uid, msg);
                perfil.setUltimoRecordatorioInactividad(hoy);
                perfilRepository.save(perfil);
                log.info("[Recordatorio] inactividad enviada a '{}' ({} días)", uid, dias);

            } catch (Exception e) {
                log.error("[Recordatorio] inactividad error para '{}': {}", uid, e.getMessage());
            }
        }
    }

    // ── 2. Budget alert — day 25 of each month at 9:00 AM Chile ──────────────
    @Scheduled(cron = "0 0 9 25 * *", zone = "America/Santiago")
    public void checkPresupuesto25() {
        LocalDate hoy = LocalDate.now(CHILE);
        int diasRestantes = hoy.lengthOfMonth() - hoy.getDayOfMonth();

        List<UsuarioPerfil> perfiles = perfilRepository.findAll();
        log.info("[Recordatorio] checkPresupuesto25: revisando {} perfiles", perfiles.size());

        for (UsuarioPerfil perfil : perfiles) {
            if (perfil.getPresupuestoMensual() == null
                    || perfil.getPresupuestoMensual().compareTo(BigDecimal.ZERO) <= 0) continue;

            String uid = perfil.getUsuarioId();
            try {
                GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(uid);
                BigDecimal presup   = perfil.getPresupuestoMensual();
                BigDecimal gastado  = resumen.totalGastado();

                double pct = gastado.divide(presup, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                if (pct < 85) continue;

                BigDecimal disponible = presup.subtract(gastado).max(BigDecimal.ZERO);
                String nombre = perfil.getNombre() != null ? perfil.getNombre() : "amigo";

                String msg = "Ojo " + nombre + ", llevas " + fmt(gastado) + " gastados "
                        + "y quedan " + diasRestantes + " días. "
                        + "Te quedan solo " + fmt(disponible) + " de presupuesto. "
                        + "Cuidado con los gastos esta semana.";

                twilioService.sendWhatsApp(uid, msg);
                log.info("[Recordatorio] presupuesto-25 enviado a '{}' ({}%)", uid, Math.round(pct));

            } catch (Exception e) {
                log.error("[Recordatorio] presupuesto-25 error para '{}': {}", uid, e.getMessage());
            }
        }
    }

    // ── 3. New month greeting — day 1 of each month at 9:00 AM Chile ─────────
    @Scheduled(cron = "0 0 9 1 * *", zone = "America/Santiago")
    public void mensajeNuevoMes() {
        LocalDate hoy = LocalDate.now(CHILE);
        String mesNombre = capitalize(hoy.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")));

        List<UsuarioPerfil> perfiles = perfilRepository.findAll();
        log.info("[Recordatorio] mensajeNuevoMes: {} perfiles, mes={}", perfiles.size(), mesNombre);

        for (UsuarioPerfil perfil : perfiles) {
            String uid = perfil.getUsuarioId();
            try {
                StringBuilder sb = new StringBuilder();
                String saludo = perfil.getNombre() != null
                        ? "Hola " + perfil.getNombre() + "! " : "";
                sb.append(saludo);
                sb.append("Nuevo mes, nueva oportunidad 💪 ");

                if (perfil.getPresupuestoMensual() != null
                        && perfil.getPresupuestoMensual().compareTo(BigDecimal.ZERO) > 0) {
                    sb.append("Tu presupuesto para ").append(mesNombre)
                      .append(" es ").append(fmt(perfil.getPresupuestoMensual())).append(". ");
                }
                sb.append("¡Registra tu primer movimiento!");

                twilioService.sendWhatsApp(uid, sb.toString().trim());
                log.info("[Recordatorio] nuevo-mes enviado a '{}'", uid);

            } catch (Exception e) {
                log.error("[Recordatorio] nuevo-mes error para '{}': {}", uid, e.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String fmt(BigDecimal amount) {
        if (amount == null) return "$0";
        long v = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        String s = String.valueOf(Math.abs(v));
        StringBuilder r = new StringBuilder();
        int cnt = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (cnt > 0 && cnt % 3 == 0) r.insert(0, '.');
            r.insert(0, s.charAt(i));
            cnt++;
        }
        return (v < 0 ? "-$" : "$") + r;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
