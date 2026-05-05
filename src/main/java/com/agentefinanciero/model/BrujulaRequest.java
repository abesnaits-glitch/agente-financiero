package com.agentefinanciero.model;

import java.util.List;
import java.util.Map;

public class BrujulaRequest {

    private String tipoEvaluacion;        // idea_nueva | negocio_existente | explorar
    private Map<String, Object> datosnegocio;
    private String region;
    private String comuna;
    private String tipoOperacion;         // fisico | online | mixto | domicilio
    private List<String> mercado;
    private Long capitalDisponible;
    private String tiempoDisponible;
    private String experiencia;
    private String socios;
    private String objetivo;
    private Long metaMinima;
    private Long metaRealista;
    private Long metaAmbiciosa;
    private String horizonte;
    private List<String> sacrificios;
    private String toleranciaRiesgo;
    private String email;
    private String telefono;
    private String plan;                  // free | esencial | pro

    public BrujulaRequest() {}

    public String getTipoEvaluacion()                            { return tipoEvaluacion; }
    public void   setTipoEvaluacion(String tipoEvaluacion)       { this.tipoEvaluacion = tipoEvaluacion; }
    public Map<String, Object> getDatosnegocio()                 { return datosnegocio; }
    public void   setDatosnegocio(Map<String, Object> d)         { this.datosnegocio = d; }
    public String getRegion()                                    { return region; }
    public void   setRegion(String region)                       { this.region = region; }
    public String getComuna()                                    { return comuna; }
    public void   setComuna(String comuna)                       { this.comuna = comuna; }
    public String getTipoOperacion()                             { return tipoOperacion; }
    public void   setTipoOperacion(String tipoOperacion)         { this.tipoOperacion = tipoOperacion; }
    public List<String> getMercado()                             { return mercado; }
    public void         setMercado(List<String> mercado)         { this.mercado = mercado; }
    public Long   getCapitalDisponible()                         { return capitalDisponible; }
    public void   setCapitalDisponible(Long capitalDisponible)   { this.capitalDisponible = capitalDisponible; }
    public String getTiempoDisponible()                          { return tiempoDisponible; }
    public void   setTiempoDisponible(String tiempoDisponible)   { this.tiempoDisponible = tiempoDisponible; }
    public String getExperiencia()                               { return experiencia; }
    public void   setExperiencia(String experiencia)             { this.experiencia = experiencia; }
    public String getSocios()                                    { return socios; }
    public void   setSocios(String socios)                       { this.socios = socios; }
    public String getObjetivo()                                  { return objetivo; }
    public void   setObjetivo(String objetivo)                   { this.objetivo = objetivo; }
    public Long   getMetaMinima()                                { return metaMinima; }
    public void   setMetaMinima(Long metaMinima)                 { this.metaMinima = metaMinima; }
    public Long   getMetaRealista()                              { return metaRealista; }
    public void   setMetaRealista(Long metaRealista)             { this.metaRealista = metaRealista; }
    public Long   getMetaAmbiciosa()                             { return metaAmbiciosa; }
    public void   setMetaAmbiciosa(Long metaAmbiciosa)           { this.metaAmbiciosa = metaAmbiciosa; }
    public String getHorizonte()                                 { return horizonte; }
    public void   setHorizonte(String horizonte)                 { this.horizonte = horizonte; }
    public List<String> getSacrificios()                         { return sacrificios; }
    public void         setSacrificios(List<String> sacrificios) { this.sacrificios = sacrificios; }
    public String getToleranciaRiesgo()                          { return toleranciaRiesgo; }
    public void   setToleranciaRiesgo(String toleranciaRiesgo)   { this.toleranciaRiesgo = toleranciaRiesgo; }
    public String getEmail()                                     { return email; }
    public void   setEmail(String email)                         { this.email = email; }
    public String getTelefono()                                  { return telefono; }
    public void   setTelefono(String telefono)                   { this.telefono = telefono; }
    public String getPlan()                                      { return plan; }
    public void   setPlan(String plan)                           { this.plan = plan; }
}
