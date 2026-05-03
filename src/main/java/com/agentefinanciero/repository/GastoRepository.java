package com.agentefinanciero.repository;

import com.agentefinanciero.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GastoRepository extends JpaRepository<Gasto, Long> {
    List<Gasto> findByUsuarioIdAndFechaBetweenOrderByFechaDescIdDesc(
            String usuarioId, LocalDate inicio, LocalDate fin);

    @Query("SELECT DISTINCT g.usuarioId FROM Gasto g WHERE g.fecha >= :desde")
    List<String> findActiveUserIds(@Param("desde") LocalDate desde);

    @Query("SELECT MAX(g.fecha) FROM Gasto g WHERE g.usuarioId = :usuarioId")
    Optional<LocalDate> findUltimaFechaActividad(@Param("usuarioId") String usuarioId);

    List<Gasto> findTop5ByUsuarioIdAndDescripcionContainingIgnoreCaseOrderByFechaDescIdDesc(
            String usuarioId, String descripcion);
}
