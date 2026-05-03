package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "categorizaciones_aprendidas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "descripcion_key"}))
public class CategorizacionAprendida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(name = "descripcion_key", nullable = false)
    private String descripcionKey;

    @Column(nullable = false)
    private String categoria;

    @Column(name = "veces_usada")
    private int vecesUsada = 1;

    @Column(name = "ultima_uso")
    private LocalDateTime ultimoUso;

    public CategorizacionAprendida() {}

    public Long getId() { return id; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getDescripcionKey() { return descripcionKey; }
    public void setDescripcionKey(String descripcionKey) { this.descripcionKey = descripcionKey; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public int getVecesUsada() { return vecesUsada; }
    public void setVecesUsada(int vecesUsada) { this.vecesUsada = vecesUsada; }

    public LocalDateTime getUltimoUso() { return ultimoUso; }
    public void setUltimoUso(LocalDateTime ultimoUso) { this.ultimoUso = ultimoUso; }
}
