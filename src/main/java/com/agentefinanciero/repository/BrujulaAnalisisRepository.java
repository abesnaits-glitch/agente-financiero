package com.agentefinanciero.repository;

import com.agentefinanciero.model.BrujulaAnalisis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrujulaAnalisisRepository extends JpaRepository<BrujulaAnalisis, Long> {
    List<BrujulaAnalisis> findByUsuarioIdOrderByCreatedAtDesc(String usuarioId);
    long countByUsuarioId(String usuarioId);
}
