package com.agentefinanciero.service;

import com.agentefinanciero.model.CategorizacionAprendida;
import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.repository.CategorizacionAprendidaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CategorizacionService {

    private static final Logger log = LoggerFactory.getLogger(CategorizacionService.class);

    private final CategorizacionAprendidaRepository repository;
    private final GastoService gastoService;

    public CategorizacionService(CategorizacionAprendidaRepository repository,
                                  GastoService gastoService) {
        this.repository   = repository;
        this.gastoService = gastoService;
    }

    // Returns the learned category for a description, if any.
    public Optional<String> buscarCategoria(String usuarioId, String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return Optional.empty();
        String norm = normalizar(descripcion);
        for (String word : norm.split("\\s+")) {
            if (word.length() < 3) continue;
            Optional<CategorizacionAprendida> found =
                    repository.findByUsuarioIdAndDescripcionKey(usuarioId, word);
            if (found.isPresent()) {
                log.info("[Cat] match aprendida: '{}' → '{}' (key='{}')",
                        descripcion, found.get().getCategoria(), word);
                return Optional.of(found.get().getCategoria());
            }
        }
        return Optional.empty();
    }

    // Called by the corregir_categoria tool: updates the most recent matching gasto + learns.
    public Map<String, Object> corregirYAprender(String usuarioId, String busqueda,
                                                   String nuevaCategoria, boolean soloAprender) {
        String cat = nuevaCategoria.toLowerCase(Locale.ROOT).trim();
        aprenderCategoria(usuarioId, busqueda, cat);
        log.info("[Cat] aprendida: usuario='{}' key='{}' → '{}'",
                usuarioId, extraerKey(busqueda), cat);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aprendido", true);
        result.put("clave", extraerKey(busqueda));
        result.put("categoria", cat);

        if (!soloAprender) {
            List<Gasto> gastos = gastoService.buscarPorDescripcion(usuarioId, busqueda);
            if (!gastos.isEmpty()) {
                Gasto g = gastos.get(0);
                String anterior = g.getCategoria() != null ? g.getCategoria() : "sin categoría";
                gastoService.actualizarCategoria(g.getId(), cat);
                result.put("ok", true);
                result.put("gastoId", g.getId());
                result.put("descripcion", g.getDescripcion());
                result.put("categoriaAnterior", anterior);
                result.put("nuevaCategoria", cat);
                log.info("[Cat] gasto {} actualizado: '{}' → '{}'", g.getId(), anterior, cat);
            } else {
                result.put("ok", true);
                result.put("mensaje", "No encontré un gasto reciente con '" + busqueda
                        + "', pero aprendí la asociación para el futuro.");
            }
        } else {
            result.put("ok", true);
        }
        return result;
    }

    private void aprenderCategoria(String usuarioId, String busqueda, String categoria) {
        String key = extraerKey(busqueda);
        CategorizacionAprendida cat = repository.findByUsuarioIdAndDescripcionKey(usuarioId, key)
                .orElseGet(CategorizacionAprendida::new);
        cat.setUsuarioId(usuarioId);
        cat.setDescripcionKey(key);
        cat.setCategoria(categoria);
        cat.setVecesUsada(cat.getVecesUsada() + 1);
        cat.setUltimoUso(LocalDateTime.now());
        repository.save(cat);
    }

    static String extraerKey(String s) {
        String norm = normalizar(s);
        for (String w : norm.split("\\s+")) {
            if (w.length() >= 3) return w;
        }
        String[] parts = norm.split("\\s+");
        return parts.length > 0 ? parts[0] : "otro";
    }

    static String normalizar(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9 ]", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return s;
    }
}
