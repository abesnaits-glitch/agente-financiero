package com.agentefinanciero.repository;

import com.agentefinanciero.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GastoRepository extends JpaRepository<Gasto, Long> {
    List<Gasto> findByUsuarioIdAndFechaBetweenOrderByFechaDescIdDesc(
            String usuarioId, LocalDate inicio, LocalDate fin);

    @Query("SELECT DISTINCT g.usuarioId FROM Gasto g WHERE g.fecha >= :desde")
    List<String> findActiveUserIds(@Param("desde") LocalDate desde);
}
