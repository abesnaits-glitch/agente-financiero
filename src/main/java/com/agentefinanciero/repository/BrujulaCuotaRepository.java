package com.agentefinanciero.repository;

import com.agentefinanciero.model.BrujulaCuota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrujulaCuotaRepository extends JpaRepository<BrujulaCuota, Long> {
    Optional<BrujulaCuota> findByUsuarioIdAndMes(String usuarioId, String mes);
}
