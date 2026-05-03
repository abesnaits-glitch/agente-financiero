package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agentes_lanzados")
public class AgenteLanzado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(nullable = false)
    private Boolean activo = false;

    @Column(name = "fecha_lanzamiento")
    private LocalDateTime fechaLanzamiento;

    @Column(name = "descripcion_corta", length = 300)
    private String descripcionCorta;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    public Long getId()                                   { return id; }
    public String getNombre()                             { return nombre; }
    public void   setNombre(String nombre)                { this.nombre = nombre; }
    public Boolean getActivo()                            { return activo; }
    public void    setActivo(Boolean activo)              { this.activo = activo; }
    public LocalDateTime getFechaLanzamiento()            { return fechaLanzamiento; }
    public void          setFechaLanzamiento(LocalDateTime t) { this.fechaLanzamiento = t; }
    public String getDescripcionCorta()                   { return descripcionCorta; }
    public void   setDescripcionCorta(String d)           { this.descripcionCorta = d; }
    public String getCheckoutUrl()                        { return checkoutUrl; }
    public void   setCheckoutUrl(String url)              { this.checkoutUrl = url; }
}
