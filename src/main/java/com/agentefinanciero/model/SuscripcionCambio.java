package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "suscripcion_cambios")
public class SuscripcionCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suscripcion_id", nullable = false)
    private Long suscripcionId;

    @Column(name = "mp_event_id", unique = true, length = 100)
    private String mpEventId;

    @Column(name = "mp_event_type", nullable = false, length = 50)
    private String mpEventType;

    @Column(name = "mp_event_action", length = 50)
    private String mpEventAction;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 20)
    private String estadoNuevo;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId()                                      { return id; }
    public Long getSuscripcionId()                           { return suscripcionId; }
    public void setSuscripcionId(Long suscripcionId)         { this.suscripcionId = suscripcionId; }
    public String getMpEventId()                             { return mpEventId; }
    public void setMpEventId(String mpEventId)               { this.mpEventId = mpEventId; }
    public String getMpEventType()                           { return mpEventType; }
    public void setMpEventType(String mpEventType)           { this.mpEventType = mpEventType; }
    public String getMpEventAction()                         { return mpEventAction; }
    public void setMpEventAction(String mpEventAction)       { this.mpEventAction = mpEventAction; }
    public String getEstadoAnterior()                        { return estadoAnterior; }
    public void setEstadoAnterior(String estadoAnterior)     { this.estadoAnterior = estadoAnterior; }
    public String getEstadoNuevo()                           { return estadoNuevo; }
    public void setEstadoNuevo(String estadoNuevo)           { this.estadoNuevo = estadoNuevo; }
    public String getDetalle()                               { return detalle; }
    public void setDetalle(String detalle)                   { this.detalle = detalle; }
    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)        { this.createdAt = createdAt; }
}
