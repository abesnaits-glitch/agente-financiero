package com.agentefinanciero.repository;

import com.agentefinanciero.model.NotificacionLanzamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificacionLanzamientoRepository extends JpaRepository<NotificacionLanzamiento, Long> {
    Optional<NotificacionLanzamiento> findByEmailIgnoreCaseAndAgente(String email, String agente);
}
