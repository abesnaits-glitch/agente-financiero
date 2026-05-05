package com.agentefinanciero.service;

import com.agentefinanciero.model.BrujulaProyecto;
import com.agentefinanciero.repository.BrujulaProyectoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class BrujulaCheckinScheduler {

    private static final Logger log = LoggerFactory.getLogger(BrujulaCheckinScheduler.class);
    private static final ZoneId CHILE = ZoneId.of("America/Santiago");

    private final BrujulaProyectoRepository proyectoRepo;
    private final BrujulaService brujulaService;

    public BrujulaCheckinScheduler(BrujulaProyectoRepository proyectoRepo,
                                   BrujulaService brujulaService) {
        this.proyectoRepo   = proyectoRepo;
        this.brujulaService = brujulaService;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "America/Santiago")
    public void procesarCheckinsPendientes() {
        LocalDateTime ahora = LocalDateTime.now(CHILE);
        List<BrujulaProyecto> pendientes = proyectoRepo.findProyectosConCheckinPendiente(ahora);

        log.info("[BrujulaCheckin] {} proyectos con check-in pendiente", pendientes.size());

        for (BrujulaProyecto proyecto : pendientes) {
            try {
                brujulaService.procesarCheckin(proyecto);
            } catch (Exception e) {
                log.error("[BrujulaCheckin] error en proyectoId={}: {}", proyecto.getId(), e.getMessage());
            }
        }

        log.info("[BrujulaCheckin] ciclo completado");
    }
}
