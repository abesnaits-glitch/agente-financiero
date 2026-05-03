package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.repository.GastoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GastoService {

    private static final Logger log = LoggerFactory.getLogger(GastoService.class);

    private final GastoRepository gastoRepository;

    public GastoService(GastoRepository gastoRepository) {
        this.gastoRepository = gastoRepository;
    }

    public Gasto registrarMovimiento(String usuarioId, BigDecimal monto, String categoria,
                                     String descripcion, String tipo, LocalDate fecha) {
        log.info("Guardando gasto: usuario={}, monto={}, categoria={}", usuarioId, monto, categoria);

        Gasto gasto = new Gasto();
        gasto.setUsuarioId(usuarioId);
        gasto.setMonto(monto);
        gasto.setCategoria(categoria != null ? categoria : "sin categoría");
        gasto.setDescripcion(descripcion != null ? descripcion : "");
        gasto.setTipo(tipo != null ? tipo : "gasto");
        gasto.setFecha(fecha != null ? fecha : LocalDate.now());

        Gasto saved = gastoRepository.save(gasto);
        log.info("Gasto guardado con ID: {} | total en BD: {}", saved.getId(), gastoRepository.count());
        return saved;
    }

    public ResumenFinanciero obtenerResumen(String usuarioId) {
        return obtenerResumen(usuarioId, YearMonth.now());
    }

    public ResumenFinanciero obtenerResumen(String usuarioId, YearMonth mes) {
        LocalDate inicio = mes.atDay(1);
        LocalDate fin    = mes.atEndOfMonth();

        log.info("Consultando gastos para usuario: {}", usuarioId);
        log.info("[DB] obtenerResumen: periodo={} a {}", inicio, fin);

        List<Gasto> movimientos = gastoRepository
                .findByUsuarioIdAndFechaBetweenOrderByFechaDescIdDesc(usuarioId, inicio, fin);

        log.info("Gastos encontrados: {}", movimientos.size());

        BigDecimal totalGastado = movimientos.stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIngresado = movimientos.stream()
                .filter(g -> "ingreso".equals(g.getTipo()))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("[DB] resumen: totalGastado={} totalIngresado={}", totalGastado, totalIngresado);

        return new ResumenFinanciero(totalGastado, totalIngresado, movimientos);
    }

    public ResumenFinanciero obtenerResumenRango(String usuarioId, LocalDate inicio, LocalDate fin) {
        log.info("[DB] obtenerResumenRango: {} -> {} para usuario={}", inicio, fin, usuarioId);
        List<Gasto> movimientos = gastoRepository
                .findByUsuarioIdAndFechaBetweenOrderByFechaDescIdDesc(usuarioId, inicio, fin);
        BigDecimal totalGastado = movimientos.stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIngresado = movimientos.stream()
                .filter(g -> "ingreso".equals(g.getTipo()))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenFinanciero(totalGastado, totalIngresado, movimientos);
    }

    public List<GrupoHormiga> detectarGastosHormiga(List<Gasto> gastos) {
        Map<String, List<Gasto>> grupos = gastos.stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .filter(g -> g.getMonto() != null && g.getMonto().compareTo(BigDecimal.valueOf(5000)) < 0)
                .collect(Collectors.groupingBy(g -> {
                    String desc = g.getDescripcion() != null && !g.getDescripcion().isBlank()
                            ? g.getDescripcion().toLowerCase().trim()
                            : (g.getCategoria() != null ? g.getCategoria().toLowerCase().trim() : "otro");
                    return desc;
                }));
        return grupos.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5)
                .map(e -> {
                    BigDecimal total = e.getValue().stream()
                            .map(Gasto::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal promedio = total.divide(
                            BigDecimal.valueOf(e.getValue().size()), 0, RoundingMode.HALF_UP);
                    return new GrupoHormiga(e.getKey(), e.getValue().size(), total, promedio);
                })
                .sorted(Comparator.comparing(GrupoHormiga::total).reversed())
                .toList();
    }

    public BigDecimal obtenerBalanceTotal(String usuarioId) {
        ResumenFinanciero total = obtenerResumenRango(
                usuarioId, LocalDate.of(2000, 1, 1), LocalDate.now());
        return total.totalIngresado().subtract(total.totalGastado());
    }

    public ProyeccionFinDeMes proyectarFinDeMes(String usuarioId) {
        LocalDate hoy = LocalDate.now();
        int diaActual = hoy.getDayOfMonth();
        if (diaActual < 7) return null;

        int diasRestantes = hoy.lengthOfMonth() - diaActual;
        ResumenFinanciero resumen = obtenerResumen(usuarioId);
        BigDecimal gastado = resumen.totalGastado();
        if (gastado.compareTo(BigDecimal.ZERO) == 0) return null;

        BigDecimal promedioDiario = gastado.divide(BigDecimal.valueOf(diaActual), 2, RoundingMode.HALF_UP);
        BigDecimal proyeccion = gastado.add(promedioDiario.multiply(BigDecimal.valueOf(diasRestantes)));
        return new ProyeccionFinDeMes(gastado, promedioDiario, proyeccion, diaActual, diasRestantes);
    }

    public List<Gasto> buscarPorDescripcion(String usuarioId, String fragmento) {
        return gastoRepository
                .findTop5ByUsuarioIdAndDescripcionContainingIgnoreCaseOrderByFechaDescIdDesc(usuarioId, fragmento);
    }

    public Gasto actualizarCategoria(Long gastoId, String nuevaCategoria) {
        Gasto g = gastoRepository.findById(gastoId)
                .orElseThrow(() -> new IllegalArgumentException("Gasto no encontrado: " + gastoId));
        g.setCategoria(nuevaCategoria);
        return gastoRepository.save(g);
    }

    public record ResumenFinanciero(
            BigDecimal totalGastado,
            BigDecimal totalIngresado,
            List<Gasto> movimientos
    ) {}

    public record GrupoHormiga(String descripcion, int cantidad, BigDecimal total, BigDecimal promedio) {}

    public record ProyeccionFinDeMes(
            BigDecimal gastadoActual,
            BigDecimal promedioDiario,
            BigDecimal proyeccion,
            int diasTranscurridos,
            int diasRestantes
    ) {}
}
