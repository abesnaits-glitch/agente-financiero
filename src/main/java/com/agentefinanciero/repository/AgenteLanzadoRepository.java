package com.agentefinanciero.repository;

import com.agentefinanciero.model.AgenteLanzado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgenteLanzadoRepository extends JpaRepository<AgenteLanzado, Long> {
    Optional<AgenteLanzado> findByNombre(String nombre);
}
