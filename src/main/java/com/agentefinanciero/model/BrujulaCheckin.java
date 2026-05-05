package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brujula_checkins")
public class BrujulaCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "mensaje_agente", nullable = false, columnDefinition = "TEXT")
    private String mensajeAgente;

    @Column(name = "respuesta_usuario", columnDefinition = "TEXT")
    private String respuestaUsuario;

    @Column(name = "resumen_ia", columnDefinition = "TEXT")
    private String resumenIa;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public BrujulaCheckin() {}

    public Long getId()                                      { return id; }
    public Long getProyectoId()                              { return proyectoId; }
    public void setProyectoId(Long proyectoId)               { this.proyectoId = proyectoId; }
    public String getMensajeAgente()                         { return mensajeAgente; }
    public void   setMensajeAgente(String mensajeAgente)     { this.mensajeAgente = mensajeAgente; }
    public String getRespuestaUsuario()                      { return respuestaUsuario; }
    public void   setRespuestaUsuario(String respuestaUsuario){ this.respuestaUsuario = respuestaUsuario; }
    public String getResumenIa()                             { return resumenIa; }
    public void   setResumenIa(String resumenIa)             { this.resumenIa = resumenIa; }
    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)       { this.createdAt = t; }
}
