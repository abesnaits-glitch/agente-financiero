-- =============================================================================
-- V3: Tabla de auditoría de cambios de estado de suscripciones
-- Registra cada transición de estado disparada por webhooks de MercadoPago.
-- Columna mp_event_id garantiza idempotencia: un mismo x-request-id de MP
-- nunca se procesa dos veces.
-- =============================================================================

CREATE TABLE IF NOT EXISTS suscripcion_cambios (
    id             BIGSERIAL    PRIMARY KEY,
    suscripcion_id BIGINT       NOT NULL REFERENCES suscripcion(id),
    mp_event_id    VARCHAR(100) UNIQUE,
    mp_event_type  VARCHAR(50)  NOT NULL,
    mp_event_action VARCHAR(50),
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20),
    detalle        TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cambios_suscripcion ON suscripcion_cambios(suscripcion_id);
CREATE INDEX IF NOT EXISTS idx_cambios_mp_event    ON suscripcion_cambios(mp_event_id);
