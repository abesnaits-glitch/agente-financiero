package com.agentefinanciero.repository;

import com.agentefinanciero.model.SuscripcionCambio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuscripcionCambioRepository extends JpaRepository<SuscripcionCambio, Long> {
    boolean existsByMpEventId(String mpEventId);
    List<SuscripcionCambio> findBySuscripcionIdOrderByCreatedAtDesc(Long suscripcionId);
}
