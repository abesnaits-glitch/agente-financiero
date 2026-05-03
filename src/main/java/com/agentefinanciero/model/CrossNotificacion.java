package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cross_notifications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "agente_destino"}))
public class CrossNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, length = 50)
    private String usuarioId;

    @Column(name = "agente_origen", nullable = false, length = 50)
    private String agenteOrigen;

    @Column(name = "agente_destino", nullable = false, length = 50)
    private String agenteDestino;

    @Column(name = "enviado", nullable = false)
    private LocalDateTime enviado = LocalDateTime.now();

    public Long getId()                               { return id; }
    public String getUsuarioId()                      { return usuarioId; }
    public void   setUsuarioId(String usuarioId)      { this.usuarioId = usuarioId; }
    public String getAgenteOrigen()                   { return agenteOrigen; }
    public void   setAgenteOrigen(String a)           { this.agenteOrigen = a; }
    public String getAgenteDestino()                  { return agenteDestino; }
    public void   setAgenteDestino(String a)          { this.agenteDestino = a; }
    public LocalDateTime getEnviado()                 { return enviado; }
    public void          setEnviado(LocalDateTime t)  { this.enviado = t; }
}
