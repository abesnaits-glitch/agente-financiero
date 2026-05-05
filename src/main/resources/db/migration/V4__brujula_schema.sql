-- =============================================================================
-- V4: Schema para el agente Brújula
-- Evaluador de ideas de negocio y consultor estratégico para LATAM.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. brujula_analisis
--    Análisis de negocios generados por Brújula. Un registro por evaluación.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brujula_analisis (
    id              BIGSERIAL    PRIMARY KEY,
    usuario_id      VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    telefono        VARCHAR(30),
    tipo_evaluacion VARCHAR(50)  NOT NULL,
    input_data      JSONB        NOT NULL,
    output_data     JSONB,
    veredicto       VARCHAR(50),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'pendiente',
    plan            VARCHAR(20)  NOT NULL DEFAULT 'free',
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_brujula_analisis_usuario ON brujula_analisis(usuario_id);
CREATE INDEX IF NOT EXISTS idx_brujula_analisis_created ON brujula_analisis(created_at DESC);


-- -----------------------------------------------------------------------------
-- 2. brujula_proyectos
--    Proyectos en seguimiento activo por WhatsApp (post-análisis).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brujula_proyectos (
    id              BIGSERIAL    PRIMARY KEY,
    analisis_id     BIGINT       NOT NULL REFERENCES brujula_analisis(id),
    usuario_id      VARCHAR(255) NOT NULL,
    nombre_proyecto VARCHAR(255) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'activo',
    proximo_checkin TIMESTAMP,
    ultimo_checkin  TIMESTAMP,
    hitos           JSONB,
    notas           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_brujula_proyectos_usuario  ON brujula_proyectos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_brujula_proyectos_checkin  ON brujula_proyectos(proximo_checkin);


-- -----------------------------------------------------------------------------
-- 3. brujula_checkins
--    Historial de check-ins de seguimiento mensual.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brujula_checkins (
    id               BIGSERIAL PRIMARY KEY,
    proyecto_id      BIGINT    NOT NULL REFERENCES brujula_proyectos(id),
    mensaje_agente   TEXT      NOT NULL,
    respuesta_usuario TEXT,
    resumen_ia       TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_brujula_checkins_proyecto ON brujula_checkins(proyecto_id);


-- -----------------------------------------------------------------------------
-- 4. brujula_cuotas
--    Cuotas de uso por usuario por mes (control de límites del plan).
--    mes en formato 'YYYY-MM'.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS brujula_cuotas (
    id             BIGSERIAL    PRIMARY KEY,
    usuario_id     VARCHAR(255) NOT NULL,
    mes            VARCHAR(7)   NOT NULL,
    analisis_count INTEGER      NOT NULL DEFAULT 0,
    plan           VARCHAR(20)  NOT NULL DEFAULT 'free',
    CONSTRAINT uq_brujula_cuotas UNIQUE (usuario_id, mes)
);

CREATE INDEX IF NOT EXISTS idx_brujula_cuotas_usuario ON brujula_cuotas(usuario_id);
