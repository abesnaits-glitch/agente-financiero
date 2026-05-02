package com.agentefinanciero.service;

import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetaService {

    private static final Logger log = LoggerFactory.getLogger(MetaService.class);
    // Isolated mapper — avoids inheriting global SNAKE_CASE naming strategy
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UsuarioPerfilRepository perfilRepository;
    private final GastoService gastoService;

    public MetaService(UsuarioPerfilRepository perfilRepository, GastoService gastoService) {
        this.perfilRepository = perfilRepository;
        this.gastoService     = gastoService;
    }

    // ── Public API (called from ClaudeService and schedulers) ────────────────

    public Map<String, Object> crearMeta(String usuarioId, String descripcion,
                                          Double monto, String fechaLimite) {
        if (monto == null || monto <= 0) {
            return Map.of("error", "Necesito el monto objetivo de la meta.");
        }
        String desc = (descripcion != null && !descripcion.isBlank()) ? descripcion.trim() : "meta de ahorro";
        String fechaNorm = normalizarFecha(fechaLimite);

        YearMonth fin = YearMonth.from(LocalDate.parse(fechaNorm));
        long mesesRestantes = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.now(), fin));

        BigDecimal balanceActual = gastoService.obtenerBalanceTotal(usuarioId);

        MetaAhorro meta = new MetaAhorro();
        meta.descripcion   = desc;
        meta.monto         = monto;
        meta.fechaLimite   = fechaNorm;
        meta.fechaInicio   = LocalDate.now().toString();
        meta.balanceInicio = balanceActual.doubleValue();
        meta.ahorroPorMes  = Math.round(monto / mesesRestantes);

        List<MetaAhorro> lista = new ArrayList<>(obtenerMetas(usuarioId));
        lista.add(meta);
        persistirMetas(usuarioId, lista);

        log.info("[Meta] creada para '{}': {} | ${} → {}", usuarioId, desc, monto, fechaNorm);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok",             true);
        r.put("descripcion",    meta.descripcion);
        r.put("montoObjetivo",  meta.monto);
        r.put("fechaLimite",    meta.fechaLimite);
        r.put("ahorroPorMes",   meta.ahorroPorMes);
        r.put("mesesRestantes", mesesRestantes);
        return r;
    }

    public Map<String, Object> progresoMetas(String usuarioId) {
        List<MetaAhorro> lista = obtenerMetas(usuarioId);
        if (lista.isEmpty()) {
            return Map.of("tieneMetas", false,
                    "mensaje", "No tienes metas de ahorro activas. Dime una y te ayudo a seguirla.");
        }
        BigDecimal balanceActual = gastoService.obtenerBalanceTotal(usuarioId);
        List<Map<String, Object>> progresos = lista.stream()
                .map(m -> calcularProgreso(m, balanceActual))
                .toList();
        return Map.of("tieneMetas", true, "metas", progresos);
    }

    public Map<String, Object> eliminarMeta(String usuarioId, Integer indice) {
        List<MetaAhorro> lista = new ArrayList<>(obtenerMetas(usuarioId));
        if (lista.isEmpty()) {
            return Map.of("error", "No tienes metas activas para eliminar.");
        }
        int idx = (indice != null) ? indice : 0;
        if (idx < 0 || idx >= lista.size()) {
            return Map.of("error", "Índice inválido. Tienes " + lista.size() + " meta(s). Usa índice 0 para la primera.");
        }
        String desc = lista.get(idx).descripcion;
        lista.remove(idx);
        persistirMetas(usuarioId, lista);
        log.info("[Meta] eliminada para '{}': {}", usuarioId, desc);
        return Map.of("ok", true, "eliminada", desc);
    }

    /** Returns the raw list of goals — used by schedulers. */
    public List<MetaAhorro> obtenerMetas(String usuarioId) {
        return perfilRepository.findById(usuarioId)
                .map(p -> parsearMetas(p.getMetasJson()))
                .orElse(List.of());
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Map<String, Object> calcularProgreso(MetaAhorro meta, BigDecimal balanceActual) {
        double ahorrado  = balanceActual.doubleValue() - meta.balanceInicio;
        double pendiente = Math.max(0, meta.monto - ahorrado);
        int    pct       = (int) Math.min(100, Math.max(0, ahorrado * 100.0 / meta.monto));

        LocalDate hoy        = LocalDate.now();
        LocalDate finDate    = LocalDate.parse(meta.fechaLimite);
        LocalDate inicioDate = LocalDate.parse(meta.fechaInicio);

        long mesesTranscurridos = ChronoUnit.MONTHS.between(YearMonth.from(inicioDate), YearMonth.from(hoy));
        long mesesRestantes     = Math.max(0, ChronoUnit.MONTHS.between(YearMonth.from(hoy), YearMonth.from(finDate)));

        double ritmoActual     = mesesTranscurridos > 0 ? ahorrado / mesesTranscurridos : 0;
        double necesarioPorMes = mesesRestantes > 0 ? pendiente / mesesRestantes : 0;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("descripcion",     meta.descripcion);
        r.put("montoObjetivo",   meta.monto);
        r.put("ahorrado",        Math.max(0, Math.round(ahorrado)));
        r.put("pendiente",       Math.round(pendiente));
        r.put("pct",             pct);
        r.put("fechaLimite",     meta.fechaLimite);
        r.put("mesesRestantes",  mesesRestantes);
        r.put("ritmoActualMes",  Math.round(ritmoActual));
        r.put("necesarioPorMes", Math.round(necesarioPorMes));
        r.put("estado",          ritmoActual >= meta.ahorroPorMes * 0.9 ? "adelantado" : "atrasado");
        return r;
    }

    private List<MetaAhorro> parsearMetas(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<MetaAhorro>>() {});
        } catch (Exception e) {
            log.warn("[Meta] error parseando metasJson: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void persistirMetas(String usuarioId, List<MetaAhorro> lista) {
        try {
            UsuarioPerfil perfil = perfilRepository.findById(usuarioId).orElse(new UsuarioPerfil());
            perfil.setUsuarioId(usuarioId);
            perfil.setMetasJson(MAPPER.writeValueAsString(lista));
            perfil.setActualizadoEn(LocalDateTime.now());
            perfilRepository.save(perfil);
        } catch (Exception e) {
            throw new RuntimeException("Error guardando metas", e);
        }
    }

    // Accepts: "diciembre 2025", "2025-12", "2025-12-31", free text → defaults 6 months out
    static String normalizarFecha(String input) {
        if (input == null || input.isBlank()) {
            return YearMonth.now().plusMonths(6).atEndOfMonth().toString();
        }
        String s = input.toLowerCase().trim();
        String[] nombres = {"enero","febrero","marzo","abril","mayo","junio",
                            "julio","agosto","septiembre","octubre","noviembre","diciembre"};
        for (int i = 0; i < nombres.length; i++) {
            if (s.contains(nombres[i])) {
                int year = LocalDate.now().getYear();
                Matcher m = Pattern.compile("(20\\d{2})").matcher(s);
                if (m.find()) year = Integer.parseInt(m.group(1));
                YearMonth ym = YearMonth.of(year, i + 1);
                if (!ym.isAfter(YearMonth.now())) ym = ym.plusYears(1);
                return ym.atEndOfMonth().toString();
            }
        }
        try { return YearMonth.parse(input).atEndOfMonth().toString(); } catch (Exception ignored) {}
        try { LocalDate.parse(input); return input; } catch (Exception ignored) {}
        return YearMonth.now().plusMonths(6).atEndOfMonth().toString();
    }

    // ── Data record ───────────────────────────────────────────────────────────

    public static class MetaAhorro {
        public String descripcion;
        public double monto;
        public String fechaLimite;
        public String fechaInicio;
        public double balanceInicio;
        public double ahorroPorMes;
    }
}
