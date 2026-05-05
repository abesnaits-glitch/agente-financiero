package com.agentefinanciero.model;

import jakarta.persistence.*;

@Entity
@Table(name = "brujula_cuotas")
public class BrujulaCuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Column(nullable = false, length = 7)
    private String mes;

    @Column(name = "analisis_count", nullable = false)
    private int analisisCount;

    @Column(nullable = false, length = 20)
    private String plan;

    public BrujulaCuota() {}

    public Long getId()                          { return id; }
    public String getUsuarioId()                 { return usuarioId; }
    public void   setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getMes()                       { return mes; }
    public void   setMes(String mes)             { this.mes = mes; }
    public int    getAnalisisCount()             { return analisisCount; }
    public void   setAnalisisCount(int count)    { this.analisisCount = count; }
    public String getPlan()                      { return plan; }
    public void   setPlan(String plan)           { this.plan = plan; }
}
