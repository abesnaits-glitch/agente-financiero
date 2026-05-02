package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.GastoRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResumenSemanalScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumenSemanalScheduler.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final GastoService gastoService;
    private final GastoRepository gastoRepository;
    private final UsuarioPerfilRepository perfilRepository;
    private final TwilioService twilioService;
    private final MetaService metaService;

    public ResumenSemanalScheduler(GastoService gastoService,
                                   GastoRepository gastoRepository,
                                   UsuarioPerfilRepository perfilRepository,
                                   TwilioService twilioService,
                                   MetaService metaService) {
        this.gastoService     = gastoService;
        this.gastoRepository  = gastoRepository;
        this.perfilRepository = perfilRepository;
        this.twilioService    = twilioService;
        this.metaService      = metaService;
    }

    @Scheduled(cron = "0 0 20 * * SUN", zone = "America/Santiago")
    public void enviarResumenSemanal() {
        LocalDate hoy         = LocalDate.now(CHILE);
        LocalDate lunesActual = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lunesPasado = lunesActual.minusWeeks(1);
        LocalDate domPasado   = lunesActual.minusDays(1);

        List<String> activeIds = gastoRepository.findActiveUserIds(hoy.minusDays(30));
        log.info("[ResumenSemanal] {} usuarios activos", activeIds.size());

        for (String usuarioId : activeIds) {
            try {
                String mensaje = construirMensaje(usuarioId, hoy, lunesActual, lunesPasado, domPasado);
                twilioService.sendWhatsApp(usuarioId, mensaje);
                log.info("[ResumenSemanal] enviado a '{}'", usuarioId);
            } catch (Exception e) {
                log.error("[ResumenSemanal] error para '{}': {}", usuarioId, e.getMessage());
            }
        }
    }

    private String construirMensaje(String usuarioId, LocalDate hoy,
            LocalDate lunesActual, LocalDate lunesPasado, LocalDate domPasado) {

        GastoService.ResumenFinanciero semana       = gastoService.obtenerResumenRango(usuarioId, lunesActual, hoy);
        GastoService.ResumenFinanciero semanaPasada = gastoService.obtenerResumenRango(usuarioId, lunesPasado, domPasado);
        GastoService.ResumenFinanciero mesActual    = gastoService.obtenerResumen(usuarioId);
        UsuarioPerfil perfil = perfilRepository.findById(usuarioId).orElse(null);

        BigDecimal gastadoSemana  = semana.totalGastado();
        BigDecimal gastadoPasada  = semanaPasada.totalGastado();
        BigDecimal diff           = gastadoSemana.subtract(gastadoPasada);

        StringBuilder sb = new StringBuilder();

        // Line 1: this week vs last week
        sb.append("Resumen de tu semana: gastaste ").append(fmt(gastadoSemana));
        if (gastadoPasada.compareTo(BigDecimal.ZERO) > 0) {
            String signo = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            sb.append(" (").append(signo).append(fmt(diff)).append(" vs semana pasada)");
        }
        sb.append(".\n");

        // Line 2: top category this week
        semana.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria() != null ? g.getCategoria() : "otro",
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> sb.append("Lo más fuerte: ").append(e.getKey())
                        .append(" con ").append(fmt(e.getValue())).append(".\n"));

        // Line 3: remaining monthly budget
        if (perfil != null && perfil.getPresupuestoMensual() != null
                && perfil.getPresupuestoMensual().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal disponible = perfil.getPresupuestoMensual().subtract(mesActual.totalGastado());
            if (disponible.compareTo(BigDecimal.ZERO) >= 0) {
                sb.append("Te quedan ").append(fmt(disponible)).append(" para terminar el mes.\n");
            } else {
                sb.append("Pasaste el presupuesto por ").append(fmt(disponible.abs())).append(".\n");
            }
        }

        // Line 4 (optional): first active goal progress
        try {
            List<MetaService.MetaAhorro> metas = metaService.obtenerMetas(usuarioId);
            if (!metas.isEmpty()) {
                Map<String, Object> prog = metaService.progresoMetas(usuarioId);
                if (Boolean.TRUE.equals(prog.get("tieneMetas"))) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> lista = (List<Map<String, Object>>) prog.get("metas");
                    if (lista != null && !lista.isEmpty()) {
                        Map<String, Object> p = lista.get(0);
                        sb.append("Meta \"").append(p.get("descripcion")).append("\": ")
                          .append("llevas $").append(p.get("ahorrado"))
                          .append(" de $").append(p.get("montoObjetivo"))
                          .append(" (").append(p.get("pct")).append("%).")
                          .append(" Vas ").append(p.get("estado")).append(".");
                    }
                }
            }
        } catch (Exception ignored) {}

        return sb.toString().trim();
    }

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
}
