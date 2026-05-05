-- =============================================================================
-- Baseline schema for Kin / agente-financiero
-- Verified against production (Supabase) on 2026-05-04
-- DO NOT MODIFY this file once committed.
-- Future schema changes must go in V2__*.sql, V3__*.sql, etc.
--
-- IMPORTANT: This file is executed ONLY when creating a brand-new database
-- (e.g., a local dev environment from scratch, or a disaster-recovery rebuild).
-- Against the existing production database in Supabase, Flyway will skip it:
-- baseline-on-migrate=true marks version 0 as already applied, and this file
-- becomes V1 which Flyway also skips on first run against an existing schema.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. gastos
--    Each financial movement (expense or income) registered by a user.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gastos (
    id          BIGSERIAL    PRIMARY KEY,
    categoria   VARCHAR(255),
    descripcion VARCHAR(255),
    fecha       DATE         NOT NULL,
    monto       NUMERIC      NOT NULL,
    tipo        VARCHAR(255) NOT NULL,
    usuario_id  VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_gastos_usuario_fecha
    ON gastos (usuario_id, fecha DESC);


-- -----------------------------------------------------------------------------
-- 2. usuario_perfil
--    Persistent user profile: name, salary, budget, goals, onboarding state.
--    Primary key is the WhatsApp number (e.g. '56912345678').
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario_perfil (
    usuario_id                      VARCHAR(255) PRIMARY KEY,
    actualizado_en                  TIMESTAMP,
    metas                           VARCHAR(2000),
    nombre                          VARCHAR(255),
    notas                           VARCHAR(2000),
    presupuesto_mensual             NUMERIC,
    sueldo_aproximado               NUMERIC,
    metas_json                      TEXT,
    ultimo_recordatorio_inactividad DATE,
    onboarding_paso                 INTEGER
);


-- -----------------------------------------------------------------------------
-- 3. suscripcion
--    Subscription record per user per agent. Controls access gate.
--    Estados: PENDIENTE | ACTIVO | CANCELADO | VENCIDO | TRIAL
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suscripcion (
    id                  BIGSERIAL    PRIMARY KEY,
    activated_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL,
    estado              VARCHAR(255) NOT NULL,
    mp_subscription_id  VARCHAR(255) UNIQUE,
    whatsapp_number     VARCHAR(255) NOT NULL,
    agente              VARCHAR(50),
    fecha_inicio        TIMESTAMP,
    fecha_proximo_pago  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suscripcion_whatsapp_estado
    ON suscripcion (whatsapp_number, estado);


-- -----------------------------------------------------------------------------
-- 4. logros_usuario
--    Achievements unlocked per user.
--    desbloqueado = timestamp of the moment the achievement was unlocked.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logros_usuario (
    id           BIGSERIAL    PRIMARY KEY,
    usuario_id   VARCHAR(255) NOT NULL,
    tipo_logro   VARCHAR(255) NOT NULL,
    desbloqueado TIMESTAMP    NOT NULL,
    CONSTRAINT uq_logros_usuario_tipo UNIQUE (usuario_id, tipo_logro)
);


-- -----------------------------------------------------------------------------
-- 5. categorizaciones_aprendidas
--    Per-user learned keyword→category associations (e.g. 'starbucks' → 'café').
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorizaciones_aprendidas (
    id              BIGSERIAL    PRIMARY KEY,
    categoria       VARCHAR(255) NOT NULL,
    descripcion_key VARCHAR(255) NOT NULL,
    ultima_uso      TIMESTAMP,
    usuario_id      VARCHAR(255) NOT NULL,
    veces_usada     INTEGER,
    CONSTRAINT uq_categorizaciones_usuario_key UNIQUE (usuario_id, descripcion_key)
);


-- -----------------------------------------------------------------------------
-- 6. comentarios
--    Reviews and testimonials shown on the landing page.
--    aprobado controls public visibility (admin approves/rejects).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comentarios (
    id         BIGSERIAL    PRIMARY KEY,
    nombre     VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    estrellas  INTEGER      NOT NULL,
    comentario TEXT         NOT NULL,
    fecha      DATE         NOT NULL,
    aprobado   BOOLEAN      NOT NULL DEFAULT TRUE
);


-- -----------------------------------------------------------------------------
-- 7. agentes_lanzados
--    Control table tracking which agents have been officially launched.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agentes_lanzados (
    id                BIGSERIAL    PRIMARY KEY,
    activo            BOOLEAN      NOT NULL,
    checkout_url      VARCHAR(500),
    descripcion_corta VARCHAR(300),
    fecha_lanzamiento TIMESTAMP,
    nombre            VARCHAR(50)  NOT NULL UNIQUE
);


-- -----------------------------------------------------------------------------
-- 8. cross_notifications
--    Tracks cross-agent notifications sent (e.g. Faro users notified about Vita).
--    Unique constraint prevents sending the same notification twice.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cross_notifications (
    id             BIGSERIAL   PRIMARY KEY,
    usuario_id     VARCHAR(50) NOT NULL,
    agente_origen  VARCHAR(50) NOT NULL,
    agente_destino VARCHAR(50) NOT NULL,
    enviado        TIMESTAMP   NOT NULL,
    CONSTRAINT uq_cross_notifications_usuario_destino UNIQUE (usuario_id, agente_destino)
);


-- -----------------------------------------------------------------------------
-- 9. notificaciones_lanzamiento
--    Waitlist: people who expressed interest in a not-yet-launched agent.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notificaciones_lanzamiento (
    id             BIGSERIAL    PRIMARY KEY,
    agente         VARCHAR(50)  NOT NULL,
    email          VARCHAR(254) NOT NULL,
    whatsapp       VARCHAR(30),
    fecha_registro TIMESTAMP    NOT NULL,
    notificado     BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notificaciones_email_agente UNIQUE (email, agente)
);


-- -----------------------------------------------------------------------------
-- 10. conversation_turns
--     Persistent conversation history per user per agent.
--     role: 'USER' | 'ASSISTANT'
--     NOTE: As of baseline date, ClaudeService uses in-memory history.
--     This table is prepared for future wiring (see Prompt 2/3).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_turns (
    id          UUID        PRIMARY KEY,
    usuario_id  VARCHAR(32) NOT NULL,
    agente      VARCHAR(32) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_turns_usuario_agente_created
    ON conversation_turns (usuario_id, agente, created_at DESC);
