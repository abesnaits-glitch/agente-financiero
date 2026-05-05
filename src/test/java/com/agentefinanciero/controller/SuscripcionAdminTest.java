package com.agentefinanciero.controller;

import com.agentefinanciero.model.Suscripcion;
import com.agentefinanciero.model.SuscripcionCambio;
import com.agentefinanciero.repository.SuscripcionCambioRepository;
import com.agentefinanciero.repository.SuscripcionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class SuscripcionAdminTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SuscripcionRepository    susRepo;
    @Autowired private SuscripcionCambioRepository cambioRepo;

    private static final String AUTH_HEADER =
            "Basic " + Base64.getEncoder().encodeToString("admin:test_admin".getBytes(StandardCharsets.UTF_8));

    private Suscripcion suscripcionFaro;

    @BeforeEach
    void crearDatosBase() {
        Suscripcion sus = new Suscripcion();
        sus.setWhatsappNumber("56911111111");
        sus.setAgente("faro");
        sus.setEstado("ACTIVO");
        sus.setCreatedAt(LocalDateTime.now());
        suscripcionFaro = susRepo.save(sus);
    }

    // ── 1. Sin auth → 401 ────────────────────────────────────────────────────

    @Test
    void listarSuscripciones_sinAuth_retorna401() throws Exception {
        mockMvc.perform(get("/admin/suscripciones"))
                .andExpect(status().isUnauthorized());
    }

    // ── 2. Con auth → 200 con la suscripción seeded ──────────────────────────

    @Test
    void listarSuscripciones_conAuth_retornaLista() throws Exception {
        mockMvc.perform(get("/admin/suscripciones")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.suscripciones[0].estado").value("ACTIVO"));
    }

    // ── 3. Filtro por estado funciona ─────────────────────────────────────────

    @Test
    void listarSuscripciones_filtroEstado_soloRetornaCoincidencias() throws Exception {
        Suscripcion cancelada = new Suscripcion();
        cancelada.setWhatsappNumber("56922222222");
        cancelada.setAgente("faro");
        cancelada.setEstado("CANCELADO");
        cancelada.setCreatedAt(LocalDateTime.now());
        susRepo.save(cancelada);

        mockMvc.perform(get("/admin/suscripciones?estado=CANCELADO")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.suscripciones[0].estado").value("CANCELADO"));
    }

    // ── 4. Cambiar estado válido → 200 + registro de auditoría ───────────────

    @Test
    void cambiarEstado_valido_actualizaYRegistraAuditoria() throws Exception {
        Long id = suscripcionFaro.getId();

        mockMvc.perform(post("/admin/suscripciones/" + id + "/cambiar-estado")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADO\",\"motivo\":\"test manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoAnterior").value("ACTIVO"))
                .andExpect(jsonPath("$.estadoNuevo").value("CANCELADO"));

        Suscripcion actualizada = susRepo.findById(id).orElseThrow();
        assertThat(actualizada.getEstado()).isEqualTo("CANCELADO");

        List<SuscripcionCambio> historial = cambioRepo.findBySuscripcionIdOrderByCreatedAtDesc(id);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getMpEventType()).isEqualTo("admin");
        assertThat(historial.get(0).getEstadoAnterior()).isEqualTo("ACTIVO");
        assertThat(historial.get(0).getEstadoNuevo()).isEqualTo("CANCELADO");
        assertThat(historial.get(0).getDetalle()).isEqualTo("test manual");
    }

    // ── 5. Estado inválido → 400 ─────────────────────────────────────────────

    @Test
    void cambiarEstado_estadoInvalido_retorna400() throws Exception {
        Long id = suscripcionFaro.getId();

        mockMvc.perform(post("/admin/suscripciones/" + id + "/cambiar-estado")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── 6. Suscripción inexistente → 404 ─────────────────────────────────────

    @Test
    void cambiarEstado_suscripcionNoExiste_retorna404() throws Exception {
        mockMvc.perform(post("/admin/suscripciones/999999/cambiar-estado")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isNotFound());
    }

    // ── 7. Historial devuelve cambios registrados ─────────────────────────────

    @Test
    void historial_retornaRegistrosEnOrdenDescendente() throws Exception {
        Long id = suscripcionFaro.getId();

        SuscripcionCambio c = new SuscripcionCambio();
        c.setSuscripcionId(id);
        c.setMpEventId("test-hist-001");
        c.setMpEventType("payment");
        c.setMpEventAction("payment.updated");
        c.setEstadoAnterior("PENDIENTE");
        c.setEstadoNuevo("ACTIVO");
        c.setCreatedAt(LocalDateTime.now());
        cambioRepo.save(c);

        mockMvc.perform(get("/admin/suscripciones/" + id + "/historial")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suscripcionId").value(id))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.historial[0].mpEventType").value("payment"));
    }

    // ── 8. Historial de suscripción inexistente → 404 ────────────────────────

    @Test
    void historial_suscripcionNoExiste_retorna404() throws Exception {
        mockMvc.perform(get("/admin/suscripciones/999999/historial")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound());
    }
}
