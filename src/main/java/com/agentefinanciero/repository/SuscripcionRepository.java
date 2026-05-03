package com.agentefinanciero.repository;

import com.agentefinanciero.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {
    Optional<Suscripcion> findByMpSubscriptionId(String mpSubscriptionId);
    Optional<Suscripcion> findByWhatsappNumber(String whatsappNumber);
    boolean existsByWhatsappNumberAndEstado(String whatsappNumber, String estado);
}
