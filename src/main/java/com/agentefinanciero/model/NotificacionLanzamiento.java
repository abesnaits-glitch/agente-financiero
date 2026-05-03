package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificaciones_lanzamiento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "agente"}))
public class NotificacionLanzamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String agente;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 30)
    private String whatsapp;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean notificado = false;

    public Long getId()                                    { return id; }
    public String getAgente()                              { return agente; }
    public void setAgente(String agente)                   { this.agente = agente; }
    public String getEmail()                               { return email; }
    public void setEmail(String email)                     { this.email = email; }
    public String getWhatsapp()                            { return whatsapp; }
    public void setWhatsapp(String whatsapp)               { this.whatsapp = whatsapp; }
    public LocalDateTime getFechaRegistro()                { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime t)          { this.fechaRegistro = t; }
    public Boolean getNotificado()                         { return notificado; }
    public void setNotificado(Boolean notificado)          { this.notificado = notificado; }
}
