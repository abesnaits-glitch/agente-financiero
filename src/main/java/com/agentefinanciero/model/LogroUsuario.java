package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "logros_usuario",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "tipo_logro"}))
public class LogroUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(name = "tipo_logro", nullable = false)
    private String tipoLogro;

    @Column(nullable = false)
    private LocalDateTime desbloqueado;

    public LogroUsuario() {}

    public LogroUsuario(String usuarioId, String tipoLogro) {
        this.usuarioId    = usuarioId;
        this.tipoLogro    = tipoLogro;
        this.desbloqueado = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getTipoLogro() { return tipoLogro; }
    public void setTipoLogro(String tipoLogro) { this.tipoLogro = tipoLogro; }

    public LocalDateTime getDesbloqueado() { return desbloqueado; }
    public void setDesbloqueado(LocalDateTime desbloqueado) { this.desbloqueado = desbloqueado; }
}
