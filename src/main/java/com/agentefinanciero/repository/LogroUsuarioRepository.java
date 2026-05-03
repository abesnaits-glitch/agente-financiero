package com.agentefinanciero.repository;

import com.agentefinanciero.model.LogroUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LogroUsuarioRepository extends JpaRepository<LogroUsuario, Long> {
    Optional<LogroUsuario> findByUsuarioIdAndTipoLogro(String usuarioId, String tipoLogro);
    List<LogroUsuario> findByUsuarioIdOrderByDesbloqueadoAsc(String usuarioId);
    boolean existsByUsuarioIdAndTipoLogro(String usuarioId, String tipoLogro);
}
