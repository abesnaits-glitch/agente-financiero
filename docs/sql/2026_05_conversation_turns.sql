-- =============================================================================
-- conversation_turns
-- Historial conversacional persistente para todos los agentes Claude de Kin.
-- Sirve a Faro, Wingman, Vita, Lilith, Nexo y cualquier agente futuro.
--
-- Schema:  public
-- Proyecto: agente-financiero
-- Creado:  2026-05-03
-- =============================================================================

CREATE TABLE public.conversation_turns (

    -- Clave primaria UUID generada automáticamente por PostgreSQL.
    id UUID NOT NULL DEFAULT gen_random_uuid(),

    -- Número de WhatsApp normalizado del usuario (sin '+', sin 'whatsapp:').
    -- Ej: '56912345678'. Coincide con usuario_id en usuario_perfil y gastos.
    usuario_id VARCHAR(32) NOT NULL,

    -- Identificador del agente que participó en este turno.
    -- Valores esperados: 'faro', 'wingman', 'vita', 'lilith', 'nexo'.
    -- Sin ENUM a nivel BD para permitir nuevos agentes sin ALTER TABLE.
    -- La validación del valor se hace en la capa Java (@Column + lógica de servicio).
    agente VARCHAR(32) NOT NULL,

    -- Rol del emisor del turno dentro de la conversación con Claude.
    -- 'USER'      → mensaje enviado por el usuario.
    -- 'ASSISTANT' → respuesta generada por Claude.
    role VARCHAR(16) NOT NULL,

    -- Contenido textual completo del turno. Sin límite de longitud (TEXT).
    -- Para el rol ASSISTANT puede incluir el resultado de tool calls ya resueltos.
    content TEXT NOT NULL,

    -- Momento exacto en que se persistió el turno, con zona horaria.
    -- Usado para ordenar la conversación y para la poda de los últimos N turnos.
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- ── Constraints ────────────────────────────────────────────────────────
    CONSTRAINT conversation_turns_pkey
        PRIMARY KEY (id),

    CONSTRAINT conversation_turns_role_check
        CHECK (role IN ('USER', 'ASSISTANT'))
);

-- =============================================================================
-- Índices
-- =============================================================================

-- Índice principal: soporta el query "dame los últimos N turnos de este usuario
-- con este agente" que se ejecuta en cada llamada a Claude.
-- El orden DESC en created_at evita un sort en memoria para LIMIT N.
CREATE INDEX idx_conv_turns_user_agent_created
    ON public.conversation_turns (usuario_id, agente, created_at DESC);

-- =============================================================================
-- Ejemplo de queries
-- =============================================================================

-- Recuperar los últimos 20 turnos de un usuario con un agente específico
-- (orden cronológico para pasarle el historial a Claude en el orden correcto):
--
-- SELECT id, role, content, created_at
-- FROM conversation_turns
-- WHERE usuario_id = '56912345678'
--   AND agente     = 'faro'
-- ORDER BY created_at DESC
-- LIMIT 20;
--
-- Nota: el resultado viene DESC (más reciente primero). En Java hay que invertir
-- la lista antes de construir los mensajes para Claude (el SDK espera orden cronológico).

-- Podar turnos antiguos, conservando solo los últimos 20 por (usuario, agente).
-- Se ejecuta después de cada INSERT desde la capa de servicio Java:
--
-- DELETE FROM conversation_turns
-- WHERE usuario_id = '56912345678'
--   AND agente     = 'faro'
--   AND id NOT IN (
--       SELECT id
--       FROM conversation_turns
--       WHERE usuario_id = '56912345678'
--         AND agente     = 'faro'
--       ORDER BY created_at DESC
--       LIMIT 20
--   );
