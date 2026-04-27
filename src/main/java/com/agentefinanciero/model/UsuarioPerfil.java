package com.agentefinanciero.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_perfil")
public class UsuarioPerfil {

    @Id
    @Column(name = "usuario_id")
    private String usuarioId;

    private String nombre;

    @Column(name = "sueldo_aproximado")
    private BigDecimal sueldoAproximado;

    @Column(name = "presupuesto_mensual")
    private BigDecimal presupuestoMensual;

    @Column(length = 2000)
    private String metas;

    @Column(length = 2000)
    private String notas;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public BigDecimal getSueldoAproximado() { return sueldoAproximado; }
    public void setSueldoAproximado(BigDecimal sueldoAproximado) { this.sueldoAproximado = sueldoAproximado; }

    public BigDecimal getPresupuestoMensual() { return presupuestoMensual; }
    public void setPresupuestoMensual(BigDecimal presupuestoMensual) { this.presupuestoMensual = presupuestoMensual; }

    public String getMetas() { return metas; }
    public void setMetas(String metas) { this.metas = metas; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
