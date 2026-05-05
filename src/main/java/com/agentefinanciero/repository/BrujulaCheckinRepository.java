package com.agentefinanciero.repository;

import com.agentefinanciero.model.BrujulaCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrujulaCheckinRepository extends JpaRepository<BrujulaCheckin, Long> {
    List<BrujulaCheckin> findByProyectoIdOrderByCreatedAtDesc(Long proyectoId);
    long countByProyectoId(Long proyectoId);
}
