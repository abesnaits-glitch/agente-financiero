package com.agentefinanciero.service;

import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PerfilService {

    private static final Logger log = LoggerFactory.getLogger(PerfilService.class);

    private final UsuarioPerfilRepository repository;

    public PerfilService(UsuarioPerfilRepository repository) {
        this.repository = repository;
    }

    public UsuarioPerfil actualizar(String usuarioId, String nombre, Double sueldoAproximado,
                                    Double presupuestoMensual, String metas, String notas) {
        UsuarioPerfil perfil = repository.findById(usuarioId).orElse(new UsuarioPerfil());
        perfil.setUsuarioId(usuarioId);

        if (nombre != null && !nombre.isBlank()) {
            perfil.setNombre(nombre.trim());
        }
        if (sueldoAproximado != null) {
            perfil.setSueldoAproximado(BigDecimal.valueOf(sueldoAproximado));
        }
        if (presupuestoMensual != null) {
            perfil.setPresupuestoMensual(BigDecimal.valueOf(presupuestoMensual));
        }
        if (metas != null && !metas.isBlank()) {
            String existentes = perfil.getMetas();
            perfil.setMetas(existentes != null ? existentes + "; " + metas.trim() : metas.trim());
        }
        if (notas != null && !notas.isBlank()) {
            String existentes = perfil.getNotas();
            perfil.setNotas(existentes != null ? existentes + "; " + notas.trim() : notas.trim());
        }
        perfil.setActualizadoEn(LocalDateTime.now());

        UsuarioPerfil saved = repository.save(perfil);
        log.info("[Perfil] actualizado para usuarioId='{}': nombre={} sueldo={} presupuesto={}",
                usuarioId, saved.getNombre(), saved.getSueldoAproximado(), saved.getPresupuestoMensual());
        return saved;
    }

    /**
     * Builds the profile section to inject into the system prompt.
     * Returns empty string if the user has no profile or all fields are null.
     */
    public String construirContexto(String usuarioId) {
        return repository.findById(usuarioId).map(p -> {
            StringBuilder sb = new StringBuilder();
            if (p.getNombre() != null)           sb.append("- Nombre: ").append(p.getNombre()).append("\n");
            if (p.getSueldoAproximado() != null) sb.append("- Sueldo aproximado: $").append(p.getSueldoAproximado().toPlainString()).append("\n");
            if (p.getPresupuestoMensual() != null) sb.append("- Presupuesto mensual: $").append(p.getPresupuestoMensual().toPlainString()).append("\n");
            if (p.getMetas() != null)            sb.append("- Metas: ").append(p.getMetas()).append("\n");
            if (p.getNotas() != null)            sb.append("- Notas: ").append(p.getNotas()).append("\n");

            String body = sb.toString();
            if (body.isBlank()) return "";
            return "PERFIL DEL USUARIO:\n" + body;
        }).orElse("");
    }
}
