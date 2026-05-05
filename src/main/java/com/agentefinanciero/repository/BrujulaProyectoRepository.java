package com.agentefinanciero.repository;

import com.agentefinanciero.model.BrujulaProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BrujulaProyectoRepository extends JpaRepository<BrujulaProyecto, Long> {
    List<BrujulaProyecto> findByUsuarioIdAndEstado(String usuarioId, String estado);
    long countByUsuarioIdAndEstado(String usuarioId, String estado);

    @Query("SELECT p FROM BrujulaProyecto p WHERE p.estado = 'activo' AND p.proximoCheckin <= :ahora")
    List<BrujulaProyecto> findProyectosConCheckinPendiente(@Param("ahora") LocalDateTime ahora);
}
