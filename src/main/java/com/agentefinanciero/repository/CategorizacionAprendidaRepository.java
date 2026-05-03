package com.agentefinanciero.repository;

import com.agentefinanciero.model.CategorizacionAprendida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategorizacionAprendidaRepository extends JpaRepository<CategorizacionAprendida, Long> {
    Optional<CategorizacionAprendida> findByUsuarioIdAndDescripcionKey(String usuarioId, String descripcionKey);
}
