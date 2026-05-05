package com.agentefinanciero.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "brujula_analisis")
public class BrujulaAnalisis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(name = "tipo_evaluacion", nullable = false, length = 50)
    private String tipoEvaluacion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", nullable = false, columnDefinition = "jsonb")
    private String inputData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_data", columnDefinition = "jsonb")
    private String outputData;

    @Column(length = 50)
    private String veredicto;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false, length = 20)
    private String plan;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BrujulaAnalisis() {}

    public Long getId()                                    { return id; }
    public String getUsuarioId()                           { return usuarioId; }
    public void   setUsuarioId(String usuarioId)           { this.usuarioId = usuarioId; }
    public String getEmail()                               { return email; }
    public void   setEmail(String email)                   { this.email = email; }
    public String getTelefono()                            { return telefono; }
    public void   setTelefono(String telefono)             { this.telefono = telefono; }
    public String getTipoEvaluacion()                      { return tipoEvaluacion; }
    public void   setTipoEvaluacion(String tipoEvaluacion) { this.tipoEvaluacion = tipoEvaluacion; }
    public String getInputData()                           { return inputData; }
    public void   setInputData(String inputData)           { this.inputData = inputData; }
    public String getOutputData()                          { return outputData; }
    public void   setOutputData(String outputData)         { this.outputData = outputData; }
    public String getVeredicto()                           { return veredicto; }
    public void   setVeredicto(String veredicto)           { this.veredicto = veredicto; }
    public String getEstado()                              { return estado; }
    public void   setEstado(String estado)                 { this.estado = estado; }
    public String getPlan()                                { return plan; }
    public void   setPlan(String plan)                     { this.plan = plan; }
    public LocalDateTime getCreatedAt()                    { return createdAt; }
    public void          setCreatedAt(LocalDateTime t)     { this.createdAt = t; }
    public LocalDateTime getUpdatedAt()                    { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime t)     { this.updatedAt = t; }
}
