package com.agentefinanciero.repository;

import com.agentefinanciero.model.Comentario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComentarioRepository extends JpaRepository<Comentario, Long> {
    List<Comentario> findByAprobadoTrueOrderByFechaDesc();
    List<Comentario> findAllByOrderByFechaDesc();
}
