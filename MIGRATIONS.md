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
