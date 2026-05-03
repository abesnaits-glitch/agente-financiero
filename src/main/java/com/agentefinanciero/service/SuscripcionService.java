package com.agentefinanciero.service;

import com.agentefinanciero.repository.SuscripcionRepository;
import org.springframework.stereotype.Service;

@Service
public class SuscripcionService {

    private static final String ESTADO_ACTIVO = "ACTIVO";

    private static final String MSG_SIN_ACCESO =
        "Hola 👋 Soy Faro de Kin.\n\n"
        + "Para usarme necesitas activar tu suscripción de $5/mes.\n\n"
        + "Actívala aquí: https://kinagentes.com/contratar/faro\n\n"
        + "Una vez actives, vuelve y empezamos 💪";

    private final SuscripcionRepository repo;

    public SuscripcionService(SuscripcionRepository repo) {
        this.repo = repo;
    }

    /** Returns true if the user has an active Faro subscription. */
    public boolean tieneAcceso(String usuarioId) {
        return repo.existsByWhatsappNumberAndEstado(usuarioId, ESTADO_ACTIVO);
    }

    public String mensajeSinAcceso() {
        return MSG_SIN_ACCESO;
    }
}
