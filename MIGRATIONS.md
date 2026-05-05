# Guía de Migraciones de Base de Datos — Kin

## Herramienta: Flyway

Flyway se ejecuta automáticamente al iniciar la aplicación. Compara las migraciones en
`src/main/resources/db/migration/` contra la tabla `flyway_schema_history` en la BD
y aplica las que faltan, en orden de versión.

---

## Cómo agregar una migración nueva

### 1. Naming convention obligatorio

```
V{N}__{descripcion_con_guiones_bajos}.sql
```

- `V` mayúscula, seguida del número de versión (entero o decimal)
- Doble guion bajo `__` entre versión y descripción
- Descripción: palabras separadas por `_`, en minúsculas
- Ejemplos correctos:
  - `V2__add_agent_brujula.sql`
  - `V3__add_index_gastos_categoria.sql`
  - `V4__alter_suscripcion_add_trial_end.sql`

### 2. Ubicación

```
src/main/resources/db/migration/V{N}__nombre.sql
```

### 3. Contenido del archivo

Usar `IF NOT EXISTS` / `IF EXISTS` siempre que sea posible para hacer las migraciones
idempotentes:

```sql
-- V2__add_agent_brujula.sql
-- Agrega soporte para el agente Brújula

ALTER TABLE suscripcion
    ADD COLUMN IF NOT EXISTS agente_version VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_suscripcion_agente
    ON suscripcion (agente);
```

### 4. Hacer commit del archivo

Una vez commiteado y pusheado, Flyway lo aplicará automáticamente en el próximo
deploy de Railway.

---

## Regla de oro: NUNCA modificar una migración ya aplicada

Una vez que un archivo `V{N}__*.sql` fue aplicado en cualquier entorno (local o
producción), **no lo toques**. Flyway guarda un checksum de cada archivo y falla
si detecta que fue modificado.

Si necesitas corregir algo de una migración ya aplicada:
- Crea `V{N+1}__fix_whatever.sql` con el SQL correctivo.
- Nunca edites el archivo original.

---

## Verificar el estado de migraciones en producción

Conectarte a Supabase y ejecutar:

```sql
SELECT version, description, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Cada fila es una migración aplicada. La versión 0 es el baseline (pre-Flyway).

---

## Comportamiento en el primer deploy con Flyway (producción)

La primera vez que la app arranca con Flyway habilitado contra la BD de Supabase:

1. Flyway detecta que no existe `flyway_schema_history`
2. La crea
3. Como `baseline-on-migrate=true` y `baseline-version=0`, inserta una fila de baseline:
   - `version=0`, `description=Pre-Flyway production state`
   - Sin ejecutar ningún SQL
4. Marca `V1__baseline_schema.sql` como aplicada (también sin ejecutarlo, porque V1 > baseline 0 pero la BD ya existe)

**Resultado:** la app arranca exactamente igual que antes. No se toca ninguna tabla.

A partir de aquí, `V2__`, `V3__`, etc. se aplicarán normalmente.

---

## Desarrollo local desde cero

Para crear un entorno local limpio con todas las tablas:

```bash
# 1. Levantar PostgreSQL local
docker run --name kin-local-db \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=kin_local \
  -p 5432:5432 \
  -d postgres:16

# 2. Correr la app con el perfil local
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

En una BD vacía, `baseline-on-migrate=false` (ver `application-local.properties`),
así que Flyway ejecuta `V1__baseline_schema.sql` y crea las 10 tablas desde cero.

---

## Qué hacer si una migración falla en producción

### Diagnóstico

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

Una migración fallida aparece con `success = false`.

### Opciones

**Opción A — La migración es idempotente (usa IF NOT EXISTS):**
Corregir el SQL, actualizar el archivo `V{N}__*.sql` (antes de que Flyway calcule el
checksum como definitivo, i.e., si el row en flyway_schema_history tiene success=false
puedes eliminar esa fila y reintentar).

```sql
-- Eliminar la entrada fallida para que Flyway la reintente
DELETE FROM flyway_schema_history WHERE version = 'N' AND success = false;
```

Luego corriges el archivo y haces redeploy.

**Opción B — La migración no es idempotente:**
1. Revertir manualmente los cambios parciales en Supabase
2. Eliminar la fila de flyway_schema_history con success=false
3. Corregir el archivo (o crear V{N+1} con fix)
4. Redeploy

### Modo de emergencia (romper el veto de Flyway)

Si necesitas deployar con urgencia y la migración fallida bloquea el arranque:

```properties
spring.flyway.enabled=false
```

Esto deshabilita Flyway temporalmente. Úsalo solo para emergencias y reactiva en el
siguiente deploy.

---

## Historial de migraciones

| Versión | Archivo | Descripción |
|---------|---------|-------------|
| 0 | (baseline) | Estado pre-Flyway: tablas creadas manualmente en Supabase |
| 1 | `V1__baseline_schema.sql` | Documentación del schema completo inicial (10 tablas) |

---

## Plan de rollback — Persistencia de conversaciones (Prompt 2/3)

### ¿Qué cambió en el código?

- `ClaudeService`: reemplazó `ConcurrentHashMap` por `ConversationTurnRepository`
- `ConversationTurnRepository`: agregó `@Transactional` a `deleteOldTurns`
- **No hay cambios de schema** (la tabla `conversation_turns` ya existía)

### ¿La nueva versión es compatible con la versión vieja en runtime?

**Sí.** Si Railway hace un deploy progresivo con dos instancias simultáneas:
- La instancia vieja (con ConcurrentHashMap) escribe en memoria, no toca BD
- La instancia nueva (con repository) escribe en `conversation_turns`
- Ambas leen/escriben de forma independiente sin conflicto
- Un usuario que rota entre instancias pierde contexto inmediato (ventana de segundos), igual que antes

### Cómo hacer rollback si algo falla en producción

**Opción A — Rollback de código (recomendada):**
```bash
# Revertir al commit anterior al feat de persistencia
git revert 1a7766f
git push
```
Esto recrea el ConcurrentHashMap. La tabla `conversation_turns` queda con datos pero no se usa. Sin daño.

**Opción B — Si el deploy ya está activo y hay errores críticos:**
1. En Railway: hacer redeploy del commit anterior desde el dashboard
2. La tabla `conversation_turns` permanece intacta — los datos no se pierden
3. Al solucionar el problema, un nuevo deploy reactiva la persistencia y retoma el historial acumulado

### Qué mirar en los primeros deploys para confirmar que funciona

En los logs de Railway, buscar estas líneas como señal de éxito:

```
[Chat] historial: N mensajes previos (BD)   ← N > 0 en usuarios con historial
```

Y ausencia de esta línea como señal de problema:

```
[Chat] error persistiendo turns para '...': ...
```

**Métricas a revisar las primeras 2 horas:**
- Tiempo de respuesta de WhatsApp: no debería aumentar más de 50ms (el query es trivial)
- Filas en `conversation_turns`: debe crecer con cada mensaje recibido
- Ausencia de errores `[Chat] error persistiendo` en Railway logs
