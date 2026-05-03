package com.agentefinanciero.repository;

import com.agentefinanciero.model.CrossNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrossNotificacionRepository extends JpaRepository<CrossNotificacion, Long> {
    boolean existsByUsuarioIdAndAgenteDestino(String usuarioId, String agenteDestino);
}
