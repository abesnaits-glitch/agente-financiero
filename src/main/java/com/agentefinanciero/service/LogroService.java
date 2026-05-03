package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.model.LogroUsuario;
import com.agentefinanciero.repository.GastoRepository;
import com.agentefinanciero.repository.LogroUsuarioRepository;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LogroService {

    private static final Logger log = LoggerFactory.getLogger(LogroService.class);

    // id → [display name, description]
    private static final Map<String, String[]> LOGROS = Map.of(
        "primer_gasto",           new String[]{"Primer Paso",          "Registraste tu primer movimiento"},
        "racha_7_dias",           new String[]{"Semana Constante",     "7 días seguidos registrando movimientos"},
        "racha_30_dias",          new String[]{"Mes Imparable",        "30 días seguidos registrando movimientos"},
        "ahorro_consecutivo_2",   new String[]{"Dos en Racha",         "Balance positivo 2 meses seguidos"},
        "ahorro_consecutivo_3",   new String[]{"Trifecta",             "Balance positivo 3 meses seguidos"},
        "redujo_gasto_categoria", new String[]{"Recorte Inteligente",  "Redujiste gastos en una categoría vs el mes anterior"},
        "meta_cumplida",          new String[]{"Meta Lograda",         "Cumpliste una meta de ahorro"},
        "presupuesto_respetado",  new String[]{"Disciplinado",         "Terminaste el mes sin pasarte del presupuesto"}
    );

    private final LogroUsuarioRepository logroRepository;
    private final GastoRepository gastoRepository;
    private final GastoService gastoService;
    private final MetaService metaService;
    private final UsuarioPerfilRepository perfilRepository;

    public LogroService(LogroUsuarioRepository logroRepository,
                        GastoRepository gastoRepository,
                        GastoService gastoService,
                        MetaService metaService,
                        UsuarioPerfilRepository perfilRepository) {
        this.logroRepository  = logroRepository;
        this.gastoRepository  = gastoRepository;
        this.gastoService     = gastoService;
        this.metaService      = metaService;
        this.perfilRepository = perfilRepository;
    }

    // Checks all achievements for the user. Returns notification messages for newly unlocked ones.
    public List<String> verificarLogros(String usuarioId) {
        List<String> nuevos = new ArrayList<>();
        try {
            nuevos.addAll(checkPrimerGasto(usuarioId));
            nuevos.addAll(checkRachas(usuarioId));
            nuevos.addAll(checkBalanceMensual(usuarioId));
            nuevos.addAll(checkPresupuesto(usuarioId));
            nuevos.addAll(checkReduccionCategoria(usuarioId));
            nuevos.addAll(checkMetaCumplida(usuarioId));
        } catch (Exception e) {
            log.error("[Logros] error verificando logros para '{}': {}", usuarioId, e.getMessage());
        }
        return nuevos;
    }

    // Builds the "mis logros" display message.
    public String buildLogrosMessage(String usuarioId) {
        List<LogroUsuario> desbloqueados = logroRepository.findByUsuarioIdOrderByDesbloqueadoAsc(usuarioId);
        Set<String> tiposDesbloqueados = desbloqueados.stream()
                .map(LogroUsuario::getTipoLogro)
                .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder("Tus logros 🏆\n\n");
        int unlocked = 0;
        for (Map.Entry<String, String[]> e : LOGROS.entrySet()) {
            boolean ganado = tiposDesbloqueados.contains(e.getKey());
            String[] info = e.getValue();
            sb.append(ganado ? "✅ " : "🔒 ");
            sb.append(info[0]).append(" — ").append(info[1]).append("\n");
            if (ganado) unlocked++;
        }
        sb.append("\n").append(unlocked).append("/").append(LOGROS.size()).append(" logros desbloqueados.");
        return sb.toString().trim();
    }

    // ── Private checks ────────────────────────────────────────────────────────

    private List<String> checkPrimerGasto(String usuarioId) {
        if (logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "primer_gasto")) return List.of();
        long count = gastoRepository.countByUsuarioId(usuarioId);
        if (count < 1) return List.of();
        String msg = otorgar(usuarioId, "primer_gasto");
        return msg != null ? List.of(msg) : List.of();
    }

    private List<String> checkRachas(String usuarioId) {
        List<String> result = new ArrayList<>();
        boolean need7  = !logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "racha_7_dias");
        boolean need30 = !logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "racha_30_dias");
        if (!need7 && !need30) return result;

        int racha = calcularRachaActual(usuarioId);
        if (need7  && racha >= 7)  { String m = otorgar(usuarioId, "racha_7_dias");  if (m != null) result.add(m); }
        if (need30 && racha >= 30) { String m = otorgar(usuarioId, "racha_30_dias"); if (m != null) result.add(m); }
        return result;
    }

    private List<String> checkBalanceMensual(String usuarioId) {
        List<String> result = new ArrayList<>();
        boolean need2 = !logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "ahorro_consecutivo_2");
        boolean need3 = !logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "ahorro_consecutivo_3");
        if (!need2 && !need3) return result;

        int mesesPos = calcularMesesPositivosConsecutivos(usuarioId);
        if (need2 && mesesPos >= 2) { String m = otorgar(usuarioId, "ahorro_consecutivo_2"); if (m != null) result.add(m); }
        if (need3 && mesesPos >= 3) { String m = otorgar(usuarioId, "ahorro_consecutivo_3"); if (m != null) result.add(m); }
        return result;
    }

    private List<String> checkPresupuesto(String usuarioId) {
        if (logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "presupuesto_respetado")) return List.of();
        // Check the most recently completed month
        YearMonth mesPasado = YearMonth.now().minusMonths(1);
        return perfilRepository.findById(usuarioId).map(p -> {
            if (p.getPresupuestoMensual() == null || p.getPresupuestoMensual().compareTo(BigDecimal.ZERO) <= 0)
                return List.<String>of();
            GastoService.ResumenFinanciero r = gastoService.obtenerResumen(usuarioId, mesPasado);
            if (r.movimientos().isEmpty()) return List.<String>of();
            if (r.totalGastado().compareTo(p.getPresupuestoMensual()) <= 0) {
                String msg = otorgar(usuarioId, "presupuesto_respetado");
                return msg != null ? List.of(msg) : List.<String>of();
            }
            return List.<String>of();
        }).orElse(List.of());
    }

    private List<String> checkReduccionCategoria(String usuarioId) {
        if (logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "redujo_gasto_categoria")) return List.of();
        YearMonth now  = YearMonth.now();
        YearMonth prev = now.minusMonths(1);
        if (LocalDate.now().getDayOfMonth() < 5) return List.of(); // too early in month

        GastoService.ResumenFinanciero rActual  = gastoService.obtenerResumen(usuarioId, now);
        GastoService.ResumenFinanciero rAnterior = gastoService.obtenerResumen(usuarioId, prev);

        if (rActual.movimientos().isEmpty() || rAnterior.movimientos().isEmpty()) return List.of();

        Map<String, BigDecimal> catActual = categoriasGastos(rActual);
        Map<String, BigDecimal> catAnterior = categoriasGastos(rAnterior);

        boolean bajo = catActual.entrySet().stream()
                .filter(e -> catAnterior.containsKey(e.getKey()))
                .filter(e -> catAnterior.get(e.getKey()).compareTo(BigDecimal.ZERO) > 0)
                .anyMatch(e -> e.getValue().compareTo(catAnterior.get(e.getKey())) < 0);

        if (!bajo) return List.of();
        String msg = otorgar(usuarioId, "redujo_gasto_categoria");
        return msg != null ? List.of(msg) : List.of();
    }

    private List<String> checkMetaCumplida(String usuarioId) {
        if (logroRepository.existsByUsuarioIdAndTipoLogro(usuarioId, "meta_cumplida")) return List.of();
        try {
            Map<String, Object> progreso = metaService.progresoMetas(usuarioId);
            if (!Boolean.TRUE.equals(progreso.get("tieneMetas"))) return List.of();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metas = (List<Map<String, Object>>) progreso.get("metas");
            if (metas == null) return List.of();
            boolean cumplida = metas.stream()
                    .anyMatch(m -> m.get("pct") instanceof Integer pct && pct >= 100);
            if (!cumplida) return List.of();
            String msg = otorgar(usuarioId, "meta_cumplida");
            return msg != null ? List.of(msg) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String otorgar(String usuarioId, String tipo) {
        try {
            logroRepository.save(new LogroUsuario(usuarioId, tipo));
            String[] info = LOGROS.getOrDefault(tipo, new String[]{tipo, ""});
            log.info("[Logros] desbloqueado '{}' para '{}'", tipo, usuarioId);
            return "🏆 ¡Logro desbloqueado! " + info[0] + ": " + info[1];
        } catch (Exception e) {
            // Race condition or duplicate — already exists
            log.debug("[Logros] ya existe '{}' para '{}': {}", tipo, usuarioId, e.getMessage());
            return null;
        }
    }

    private int calcularRachaActual(String usuarioId) {
        List<LocalDate> fechas = gastoRepository.findFechasConActividad(usuarioId);
        if (fechas.isEmpty()) return 0;
        Set<LocalDate> days = new HashSet<>(fechas);
        LocalDate hoy = LocalDate.now();
        // Streak counts only if today or yesterday has activity (still active)
        LocalDate cursor = days.contains(hoy) ? hoy
                : (days.contains(hoy.minusDays(1)) ? hoy.minusDays(1) : null);
        if (cursor == null) return 0;
        int racha = 0;
        while (days.contains(cursor)) {
            racha++;
            cursor = cursor.minusDays(1);
        }
        return racha;
    }

    private int calcularMesesPositivosConsecutivos(String usuarioId) {
        int count = 0;
        YearMonth m = YearMonth.now().minusMonths(1); // start from last complete month
        for (int i = 0; i < 3; i++, m = m.minusMonths(1)) {
            GastoService.ResumenFinanciero r = gastoService.obtenerResumen(usuarioId, m);
            if (r.movimientos().isEmpty()) break;
            BigDecimal balance = r.totalIngresado().subtract(r.totalGastado());
            if (balance.compareTo(BigDecimal.ZERO) > 0) count++;
            else break;
        }
        return count;
    }

    private static Map<String, BigDecimal> categoriasGastos(GastoService.ResumenFinanciero r) {
        return r.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria() != null ? g.getCategoria().toLowerCase(Locale.ROOT) : "otro",
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));
    }
}
