package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brujula_proyectos")
public class BrujulaProyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analisis_id", nullable = false)
    private Long analisisId;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(name = "nombre_proyecto", nullable = false)
    private String nombreProyecto;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "proximo_checkin")
    private LocalDateTime proximoCheckin;

    @Column(name = "ultimo_checkin")
    private LocalDateTime ultimoCheckin;

    @Column(columnDefinition = "jsonb")
    private String hitos;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BrujulaProyecto() {}

    public Long getId()                                      { return id; }
    public Long getAnalisisId()                              { return analisisId; }
    public void setAnalisisId(Long analisisId)               { this.analisisId = analisisId; }
    public String getUsuarioId()                             { return usuarioId; }
    public void   setUsuarioId(String usuarioId)             { this.usuarioId = usuarioId; }
    public String getNombreProyecto()                        { return nombreProyecto; }
    public void   setNombreProyecto(String nombreProyecto)   { this.nombreProyecto = nombreProyecto; }
    public String getEstado()                                { return estado; }
    public void   setEstado(String estado)                   { this.estado = estado; }
    public LocalDateTime getProximoCheckin()                 { return proximoCheckin; }
    public void          setProximoCheckin(LocalDateTime t)  { this.proximoCheckin = t; }
    public LocalDateTime getUltimoCheckin()                  { return ultimoCheckin; }
    public void          setUltimoCheckin(LocalDateTime t)   { this.ultimoCheckin = t; }
    public String getHitos()                                 { return hitos; }
    public void   setHitos(String hitos)                     { this.hitos = hitos; }
    public String getNotas()                                 { return notas; }
    public void   setNotas(String notas)                     { this.notas = notas; }
    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)       { this.createdAt = t; }
}
