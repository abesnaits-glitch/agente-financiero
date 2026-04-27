package com.agentefinanciero.repository;

import com.agentefinanciero.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GastoRepository extends JpaRepository<Gasto, Long> {
    List<Gasto> findByUsuarioIdAndFechaBetweenOrderByFechaDesc(
            String usuarioId, LocalDate inicio, LocalDate fin);
}
