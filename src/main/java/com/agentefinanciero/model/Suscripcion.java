package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "suscripcion")
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whatsapp_number", nullable = false)
    private String whatsappNumber;

    // Agente al que corresponde esta suscripción (faro, vita, lilith, nexo)
    @Column(length = 50)
    private String agente;

    @Column(name = "mp_subscription_id", unique = true)
    private String mpSubscriptionId;

    @Column(nullable = false)
    private String estado; // PENDIENTE | ACTIVO | CANCELADO | VENCIDO | TRIAL

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_proximo_pago")
    private LocalDateTime fechaProximoPago;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    public Long getId()                                        { return id; }
    public String getWhatsappNumber()                          { return whatsappNumber; }
    public void   setWhatsappNumber(String w)                  { this.whatsappNumber = w; }
    public String getAgente()                                  { return agente; }
    public void   setAgente(String agente)                     { this.agente = agente; }
    public String getMpSubscriptionId()                        { return mpSubscriptionId; }
    public void   setMpSubscriptionId(String id)               { this.mpSubscriptionId = id; }
    public String getEstado()                                  { return estado; }
    public void   setEstado(String estado)                     { this.estado = estado; }
    public LocalDateTime getFechaInicio()                      { return fechaInicio; }
    public void          setFechaInicio(LocalDateTime t)       { this.fechaInicio = t; }
    public LocalDateTime getFechaProximoPago()                 { return fechaProximoPago; }
    public void          setFechaProximoPago(LocalDateTime t)  { this.fechaProximoPago = t; }
    public LocalDateTime getCreatedAt()                        { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)         { this.createdAt = t; }
    public LocalDateTime getActivatedAt()                      { return activatedAt; }
    public void          setActivatedAt(LocalDateTime t)       { this.activatedAt = t; }
}
