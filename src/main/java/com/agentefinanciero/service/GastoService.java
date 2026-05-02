package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.repository.GastoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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

    public record ResumenFinanciero(
            BigDecimal totalGastado,
            BigDecimal totalIngresado,
            List<Gasto> movimientos
    ) {}
}
