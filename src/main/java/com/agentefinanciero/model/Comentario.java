package com.agentefinanciero.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "comentarios")
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 200)
    private String email;

    @Column(nullable = false)
    private Integer estrellas;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comentario;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private Boolean aprobado = true;

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }
    public String getNombre()                    { return nombre; }
    public void setNombre(String nombre)         { this.nombre = nombre; }
    public String getEmail()                     { return email; }
    public void setEmail(String email)           { this.email = email; }
    public Integer getEstrellas()                { return estrellas; }
    public void setEstrellas(Integer estrellas)  { this.estrellas = estrellas; }
    public String getComentario()                { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDate getFecha()                  { return fecha; }
    public void setFecha(LocalDate fecha)        { this.fecha = fecha; }
    public Boolean getAprobado()                 { return aprobado; }
    public void setAprobado(Boolean aprobado)    { this.aprobado = aprobado; }
}
