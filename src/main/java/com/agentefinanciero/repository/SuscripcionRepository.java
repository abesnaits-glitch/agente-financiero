package com.agentefinanciero.repository;

import com.agentefinanciero.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByMpSubscriptionId(String mpSubscriptionId);
    Optional<Suscripcion> findByWhatsappNumber(String whatsappNumber);
    boolean existsByWhatsappNumberAndEstado(String whatsappNumber, String estado);
    List<Suscripcion> findByAgenteAndEstadoAndCreatedAtBefore(String agente, String estado, LocalDateTime before);

    List<Suscripcion> findAllByOrderByCreatedAtDesc();
    List<Suscripcion> findByAgenteOrderByCreatedAtDesc(String agente);
    List<Suscripcion> findByEstadoOrderByCreatedAtDesc(String estado);
    List<Suscripcion> findByAgenteAndEstadoOrderByCreatedAtDesc(String agente, String estado);
}
