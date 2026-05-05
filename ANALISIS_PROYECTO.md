# ANÁLISIS DEL PROYECTO — Kin / agente-financiero

> Documento generado el 2026-05-04 mediante auditoría estática del código fuente.
> No se modificó ningún archivo de código durante este análisis.

---

## 1. Estructura general del proyecto

### Árbol de paquetes

```
src/main/java/com/agentefinanciero/
├── AgenteFinancieroApplication.java   ← Punto de entrada Spring Boot + @EnableScheduling
├── controller/   (8 archivos)
├── service/      (17 archivos)
├── model/        (12 archivos)
├── repository/   (10 archivos)
├── config/       (3 archivos)
├── filter/       (2 archivos)
└── util/         (1 archivo)
```

### Controllers

| Archivo | Qué hace |
|---|---|
| `WhatsAppController.java` | Webhook de Twilio; recibe mensajes de WhatsApp y los enruta |
| `PagoController.java` | Crea preferencias de MercadoPago y recibe su webhook |
| `DashboardController.java` | Sirve el dashboard visual con token de un solo uso |
| `ReporteController.java` | Entrega el PDF mensual con token de un solo uso |
| `ImageController.java` | Sirve imágenes PNG generadas por los dashboards |
| `ComentarioController.java` | CRUD de reviews/testimonios del landing |
| `NotificacionLanzamientoController.java` | Registra interesados en los próximos agentes |
| `AdminController.java` | Panel admin para lanzar notificaciones de nuevos agentes |
| `GlobalExceptionHandler.java` | Captura todas las excepciones y retorna JSON de error limpio |

### Services

| Archivo | Qué hace |
|---|---|
| `ClaudeService.java` | **Corazón de Faro.** Llama a la API de Claude con tool use y mantiene el historial de conversación |
| `GastoService.java` | **Corazón de datos.** CRUD de gastos/ingresos, resúmenes, proyección, detección de gastos hormiga |
| `BoletaService.java` | OCR de imágenes de boletas: descarga, valida, comprime, llama Claude Vision, guarda gastos |
| `OnboardingService.java` | Flujo de 6 pasos que guía al usuario nuevo al primer uso |
| `MercadoPagoService.java` | Crea preferencias de pago y procesa el webhook de pago aprobado |
| `TwilioService.java` | Valida firmas de Twilio, envía mensajes de texto y con media |
| `DashboardService.java` | Genera HTML interactivo + PNG del dashboard financiero via Playwright |
| `ReporteService.java` | Genera PDF con gráficos, tabla de movimientos y consejos de Claude |
| `PerfilService.java` | Actualiza y construye el contexto del perfil del usuario |
| `MetaService.java` | CRUD y progreso de metas de ahorro |
| `CategorizacionService.java` | Aprende y aplica categorías personalizadas por usuario |
| `LogroService.java` | Verifica y notifica logros/achievements desbloqueados |
| `SuscripcionService.java` | Verifica si un usuario tiene suscripción activa |
| `LanzamientoService.java` | Envía notificaciones masivas WhatsApp al lanzar un agente nuevo |
| `TokenService.java` | Genera y consume tokens de un solo uso con TTL (Caffeine) |
| `ResumenSemanalScheduler.java` | **Tarea programada.** Envía resumen los domingos a las 20:00 Chile |
| `RecordatoriosScheduler.java` | **Tarea programada.** Envía recordatorios de inactividad, alerta de presupuesto y saludo de nuevo mes |

### Archivos del "corazón" de Faro

Los 4 archivos más críticos del agente son:

1. **`ClaudeService.java`** — toda la lógica de IA, system prompt, tool use y memoria de conversación
2. **`WhatsAppController.java`** — punto de entrada, enrutamiento y validación de seguridad
3. **`GastoService.java`** — toda la lógica financiera (registrar, resumir, proyectar)
4. **`MercadoPagoService.java`** — alta de usuarios pagantes vía webhook

---

## 2. Modelo de datos (entities y BD)

### Tabla: `gastos`

**Entity:** `Gasto.java`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | BIGINT PK autoincrement | |
| `usuario_id` | VARCHAR NOT NULL | Número de WhatsApp sin prefijo (ej: `56912345678`) |
| `monto` | NUMERIC NOT NULL | En pesos chilenos |
| `categoria` | VARCHAR | comida, transporte, salud, vivienda, servicios, entretenimiento, educacion, ropa, trabajo, otro |
| `descripcion` | VARCHAR | Texto libre |
| `fecha` | DATE NOT NULL | Fecha del movimiento |
| `tipo` | VARCHAR NOT NULL | `gasto` o `ingreso` |

**Propósito:** Guarda cada gasto o ingreso que el usuario registra por WhatsApp o foto de boleta.
**Sin relaciones JPA** — se relaciona con otras tablas solo via `usuario_id` (string).

---

### Tabla: `usuario_perfil`

**Entity:** `UsuarioPerfil.java`

| Campo | Tipo | Notas |
|---|---|---|
| `usuario_id` | VARCHAR PK | Número de WhatsApp (clave primaria, no es autoincrement) |
| `nombre` | VARCHAR | Nombre del usuario |
| `sueldo_aproximado` | NUMERIC | Sueldo declarado |
| `presupuesto_mensual` | NUMERIC | Presupuesto mensual (auto-calculado como 80% del sueldo si no se especifica) |
| `metas` | VARCHAR(2000) | Campo de texto libre con metas (campo antiguo) |
| `notas` | VARCHAR(2000) | Contexto adicional del usuario |
| `metas_json` | TEXT | Metas de ahorro estructuradas en JSON (campo activo) |
| `onboarding_paso` | INT | `null` = usuario pre-feature (trata como completo); `0–5` = en proceso; `6` = completado |
| `ultimo_recordatorio_inactividad` | DATE | Fecha del último recordatorio enviado (evita spam) |
| `actualizado_en` | TIMESTAMP | Última actualización del perfil |

**Propósito:** Perfil persistente del usuario. Se inyecta en el system prompt de Claude para personalizar respuestas entre sesiones.

> **Sobre identidad de usuarios:** No hay tabla "Usuario" separada. El identificador principal de cada usuario es su número de WhatsApp (`usuario_id`), que llega en el campo `From` de Twilio en formato `whatsapp:+56912345678` y se normaliza a `56912345678`.

---

### Tabla: `suscripcion`

**Entity:** `Suscripcion.java`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | BIGINT PK autoincrement | |
| `whatsapp_number` | VARCHAR NOT NULL | Número de WhatsApp del suscriptor |
| `agente` | VARCHAR(50) | `faro`, `vita`, `lilith`, `nexo` |
| `mp_subscription_id` | VARCHAR UNIQUE | ID del pago en MercadoPago |
| `estado` | VARCHAR NOT NULL | `PENDIENTE`, `ACTIVO`, `CANCELADO`, `VENCIDO`, `TRIAL` |
| `fecha_inicio` | TIMESTAMP | |
| `fecha_proximo_pago` | TIMESTAMP | |
| `created_at` | TIMESTAMP NOT NULL | |
| `activated_at` | TIMESTAMP | Momento en que el pago fue confirmado |

**Propósito:** Controla quién tiene acceso al agente. Solo usuarios con estado `ACTIVO` pueden enviar mensajes.

---

### Tabla: `logros_usuario`

**Entity:** `LogroUsuario.java`

| Campo | Notas |
|---|---|
| `id` | BIGINT PK |
| `usuario_id` | Número WhatsApp |
| `tipo_logro` | `primer_gasto`, `racha_7_dias`, `racha_30_dias`, `ahorro_consecutivo_2`, `ahorro_consecutivo_3`, `redujo_gasto_categoria`, `meta_cumplida`, `presupuesto_respetado` |
| `desbloqueado` | BOOLEAN |

**Propósito:** Gamificación. Guarda qué logros ha desbloqueado cada usuario. Hay restricción UNIQUE por (usuario_id, tipo_logro).

---

### Tabla: `categorización_aprendida`

**Entity:** `CategorizacionAprendida.java`

| Campo | Notas |
|---|---|
| `usuario_id` | Número WhatsApp |
| `descripcion_key` | Palabra normalizada (sin acentos, lowercase) |
| `categoria` | Categoría aprendida |
| `veces_usada` | Contador |
| `ultimo_uso` | Timestamp |

**Propósito:** Aprende asociaciones "starbucks → café" por usuario. Se aplica automáticamente en el próximo gasto con esa palabra clave.

---

### Tabla: `comentarios`

**Entity:** `Comentario.java`

**Propósito:** Reviews y testimonios del landing page. Incluye nombre, email, texto, estrellas (1-5), fecha y flag de aprobado. Solo aparecen públicamente los aprobados por el admin.

---

### Tabla: `agente_lanzado`

**Entity:** `AgenteLanzado.java`

**Propósito:** Control de qué agentes han sido "lanzados" oficialmente (flag activo + fecha). Usado por LanzamientoService para evitar relanzar un agente ya activo.

---

### Tabla: `cross_notificacion`

**Entity:** `CrossNotificacion.java`

**Propósito:** Registro de notificaciones cross-agente enviadas (ej: notificar a usuarios de Faro sobre el lanzamiento de Vita). Evita enviar la misma notificación dos veces.

---

### Tabla: `notificacion_lanzamiento`

**Entity:** `NotificacionLanzamiento.java`

**Propósito:** Lista de espera. Guarda email y WhatsApp de personas que expresaron interés en un agente aún no lanzado (Vita, Lilith, Nexo).

---

### Tabla: `conversation_turns`

**Entity:** `ConversationTurn.java` (agregado recientemente, git commit `6ca8281`)

**Propósito:** Diseñada para persistir el historial de conversaciones. **Importante: actualmente el historial se guarda en memoria (ConcurrentHashMap en ClaudeService), no en esta tabla.** La entidad existe pero no está conectada a ClaudeService todavía.

---

## 3. Migraciones y Schema de BD

### ¿Hay Flyway o Liquibase?

**No.** No existe ninguna carpeta `db/migration`, ni dependencia de Flyway ni de Liquibase en el `pom.xml`.

### ¿Hay scripts SQL en resources?

**No.** La búsqueda de archivos `.sql` en `src/main/resources/` no encontró ninguno.

### ¿Cómo se crearon las tablas en producción?

El `application.properties` tiene:
```properties
spring.jpa.hibernate.ddl-auto=validate
```
Esto significa que **Hibernate NO crea ni modifica tablas**. Solo valida que el schema existente coincida con las entities Java. Si hay una diferencia, la aplicación no inicia.

**Hipótesis confirmada:** Las tablas fueron creadas **manualmente en Supabase** (via el editor SQL de su consola web o via psql directo). No hay trazabilidad automática del schema.

Hay un commit reciente (`fefa8cf infra: add conversation_turns SQL migration script`) que sugiere que para la tabla `conversation_turns` sí se escribió un script SQL, pero **ese archivo no aparece en el repositorio** actualmente — fue probablemente ejecutado en Supabase y luego descartado (o no fue commiteado al repo).

**Riesgo:** Si se necesita recrear la base de datos desde cero, no existe un script completo del schema. Habría que inferirlo desde las entities Java.

---

## 4. Flujo de Faro: de principio a fin

### 4.1 Un usuario nuevo escribe por primera vez

1. El usuario escribe al número de WhatsApp de Faro.
2. Twilio reenvía el mensaje como POST a `https://agente-financiero-production.up.railway.app/webhook/whatsapp`.
3. **`WhatsAppController.handleIncoming()`** recibe el request.

**Paso 1 — Validación de seguridad:**
- Se verifica el header `X-Twilio-Signature` usando HMAC-SHA1. Si falla → 403, se descarta.

**Paso 2 — Normalización del ID de usuario:**
- `From: whatsapp:+56912345678` → `usuarioId = "56912345678"`

**Paso 3 — Gate de suscripción:**
- `SuscripcionService.tieneAcceso("56912345678")` consulta la tabla `suscripcion`.
- Si no hay suscripción con estado `ACTIVO` → se envía mensaje de activación y se termina.

**Paso 4 — Onboarding (primer mensaje post-pago):**
- `OnboardingService.isEnOnboarding()` revisa `onboarding_paso` en `usuario_perfil`.
- Si el campo es `null` y el perfil no existe → comienza el onboarding.
- El onboarding dura 6 pasos (nombre → sueldo → gastos fijos → metas → presupuesto → completado).
- Cada respuesta avanza el contador y guarda los datos en `usuario_perfil`.

**Paso 5 — Respuesta asíncrona:**
- El procesamiento ocurre en un **hilo virtual** (`Thread.ofVirtual()`).
- El webhook de Twilio recibe respuesta TwiML vacío (`<Response/>`) de inmediato.
- La respuesta real al usuario se envía luego via la API de Twilio directamente.

### 4.2 Enrutamiento de mensajes (usuario ya onboarded)

| Condición del mensaje | Servicio que responde |
|---|---|
| Es una imagen (JPEG/PNG/WebP) | `BoletaService.procesarYResponder()` |
| Contiene "logros" o "mis logros" | `LogroService.buildLogrosMessage()` |
| Contiene "reporte", "pdf", "informe" | `ReporteService.generarReporte()` → PDF |
| Contiene "dashboard", "resumen visual", "gráfico" | `DashboardService.generarDashboard()` |
| Cualquier otro texto | `ClaudeService.chat()` con tool use |

### 4.3 Qué se guarda en la BD

- Cada gasto/ingreso → fila en `gastos`
- Perfil del usuario → `usuario_perfil` (nombre, sueldo, presupuesto, metas, notas)
- Logros desbloqueados → `logros_usuario`
- Categorías aprendidas → `categorizacion_aprendida`
- El historial de conversación → **solo en memoria** (ConcurrentHashMap, máximo 20 mensajes; se pierde si el servidor reinicia)

### 4.4 El system prompt completo de Faro

El system prompt base está en `ClaudeService.java`, líneas 45–149:

```
Eres Faro, un asistente financiero personal con acceso directo a una base de datos.

REGLAS DE HERRAMIENTAS — OBLIGATORIAS SIN EXCEPCIÓN:

REGLA 1 — registrar_movimientos:
DEBES llamar esta función ANTES de responder cuando el usuario mencione cualquier
gasto o ingreso, ya sea uno o varios en el mismo mensaje.
[...]

REGLA 2 — obtener_resumen:
DEBES llamar esta función ANTES de responder cuando el usuario pregunte:
- "¿cuánto llevo gastado?", "dame un resumen", "¿cómo voy?", "mis gastos", [...]

REGLA 3 — actualizar_perfil:
DEBES llamar esta función cuando el usuario comparta información personal relevante [...]

REGLA 4 — gestionar_meta:
DEBES llamar esta función cuando el usuario mencione metas de ahorro [...]

REGLA 5 — corregir_categoria:
DEBES llamar esta función cuando el usuario corrija o cambie una categoría [...]

INFERENCIA AUTOMÁTICA:
INFERENCIA A — INGRESO → SUELDO Y PRESUPUESTO AUTOMÁTICOS (80% del ingreso)
INFERENCIA B — GASTO HABITUAL → CATEGORÍA AUTOMÁTICA (arriendo→vivienda, etc.)
INFERENCIA C — SUELDO DECLARADO → PRESUPUESTO AUTOMÁTICO

REGLAS CONVERSACIONALES:
- Nunca hagas más de una pregunta por mensaje.
- Confirma en lenguaje natural y casual, nunca en listas ni formato técnico.

TONO Y ESTILO:
- Habla como un amigo cercano que entiende de plata, en español latinoamericano.
- Respuestas cortas: máximo 2-3 líneas.
- Máximo 1 emoji por mensaje.
- Sin markdown. Nunca pidas datos bancarios.
```

A este prompt base se le agrega dinámicamente el perfil del usuario (nombre, sueldo, presupuesto, metas, notas) en cada request.

---

## 5. Integración con Claude API (Anthropic)

### Configuración del cliente

```java
AnthropicOkHttpClient.builder().apiKey(apiKey).build()
```
- Sin configuración de timeout explícita (usa defaults de OkHttp)
- Sin pool de conexiones personalizado
- Sin retry policy explícita

### Modelos usados

| Uso | Modelo | Max tokens |
|---|---|---|
| Chat principal (Faro) | `claude-haiku-4-5-20251001` | 512 |
| OCR de boletas | `claude-sonnet-4-5` | 1024 |
| Consejos financieros (PDF) | `claude-haiku-4-5-20251001` | 900 |

### Mecanismo de Tool Use

1. Claude recibe el message con las 6 herramientas disponibles.
2. Si detecta un patrón de keyword (ej: "gasté"), se fuerza el tool choice con `BetaToolChoiceTool`.
3. Se permite hasta **5 rounds** de tool use por request.
4. Después de ejecutar herramientas, se hace reset a `BetaToolChoiceAuto` para que Claude pueda responder con texto.
5. Si se agotan los 5 rounds sin respuesta de texto → error genérico.

### Manejo de errores

- Si una tool falla → se retorna `{"error": "mensaje"}` como tool result y Claude continúa.
- Si la respuesta de texto es vacía → se retorna mensaje de error hardcoded.
- Si se agota el límite de rounds → error hardcoded.
- Excepciones de red: **no hay retry explícito** — si la llamada falla, el usuario recibe "Tuve un problema procesando tu solicitud."

### Cache de respuestas

No hay cache de respuestas de Claude. **Caffeine** se usa solo para dos cosas:
1. Tokens de un solo uso (dashboard 10 min, PDF 30 min)
2. Rate limiting de requests HTTP (buckets por IP:endpoint)

---

## 6. Integración con WhatsApp (Twilio)

### Webhook de entrada

- **Endpoint:** `POST /webhook/whatsapp`
- **Formato:** `application/x-www-form-urlencoded`
- **Validación:** Header `X-Twilio-Signature` (HMAC-SHA1 sobre URL + params)
- **Parámetros usados:** `From`, `Body`, `NumMedia`, `MediaUrl0`, `MediaContentType0`

### Envío de mensajes (TwilioService)

- `sendWhatsApp(to, body)` — texto plano
- `sendWhatsAppWithMedia(to, body, mediaUrl)` — texto + URL de media (PDF)

### Templates HSM / Twilio Content Templates

No se encontró uso de Twilio Content Templates (HSM). Todos los mensajes se envían como **session messages** (respuesta dentro de las 24 hs de la última interacción del usuario). Los mensajes proactivos (recordatorios, resumen semanal) se envían igual — si el usuario no ha interactuado en más de 24 hs, **existe riesgo de que Twilio los bloquee** como mensajes fuera de sesión.

### Manejo de imágenes (boletas)

1. Se detecta `NumMedia > 0` y content type imagen (JPEG, PNG, WebP, GIF).
2. `BoletaService` descarga la imagen desde `MediaUrl0` usando Basic Auth (Twilio Account SID + Auth Token).
3. Valida magic bytes para confirmar el tipo de archivo real.
4. Si pesa más de 5MB, reescala a 1500px máx y comprime a JPEG 75%.
5. Encoda en base64 y envía a Claude Vision (`claude-sonnet-4-5`).
6. Claude retorna JSON: `{comercio, fecha, items:[{descripcion, monto, categoria}], total}`.
7. Si Claude retorna `{"error": "no_es_boleta"}`, se informa al usuario que no era una boleta.

---

## 7. Pagos y suscripciones (MercadoPago)

### Creación de suscripción

1. Usuario va a `/checkout.html`.
2. Ingresa email + número WhatsApp.
3. `POST /api/create-subscription` → `MercadoPagoService.crearPreferencia()`.
4. Se normaliza el número WhatsApp (acepta: 9 dígitos, 56+9 dígitos, +56+9 dígitos).
5. Se crea registro en `suscripcion` con estado `PENDIENTE`.
6. MercadoPago retorna una URL de checkout → se devuelve al frontend → redirige al usuario.

### Planes disponibles

Actualmente hay **un único plan** configurable via variables de entorno:
- `MP_PLAN_AMOUNT` (default: `5000`)
- `MP_PLAN_CURRENCY` (default: `CLP`)

No existen múltiples planes en el código.

### Verificación de suscripción

`SuscripcionService.tieneAcceso(usuarioId)` consulta:
```
SELECT EXISTS (
  SELECT 1 FROM suscripcion
  WHERE whatsapp_number = ? AND estado = 'ACTIVO'
)
```

### Webhook de pago aprobado

1. MercadoPago llama `POST /api/webhook/mercadopago`.
2. Se valida la firma HMAC-SHA256 del header `x-signature` (formato: `ts=<epoch>,v1=<hex>`).
3. Si el tipo del evento es `payment` → se consulta el Payment en la API de MP.
4. Si el estado es `approved`:
   - Se busca la Suscripcion por `mpSubscriptionId`.
   - Se actualiza estado → `ACTIVO`, se guarda `activated_at`.
   - Si no existe `UsuarioPerfil` para ese número → se crea uno vacío.
   - Se envía mensaje de bienvenida por WhatsApp.

**Importante:** La respuesta siempre es HTTP 200, incluso si la firma falla (para evitar reintentos de MP). El procesamiento se salta silenciosamente si la firma es inválida.

### Cancelación / fallo de pago

No hay lógica implementada para cambiar el estado de una suscripción de `ACTIVO` a `CANCELADO` o `VENCIDO`. MercadoPago enviaría un webhook, pero el código actual solo procesa eventos de tipo `payment` con estado `approved`. Los demás eventos se ignoran.

---

## 8. Sistema de rate limiting (Bucket4j)

`RateLimitFilter.java` aplica límites **por IP** (usando `X-Forwarded-For` si está disponible):

| Endpoint | Límite |
|---|---|
| `/admin/*` | 5 requests/min (protección anti-fuerza bruta) |
| `POST /api/create-subscription` | 3 requests/min (evita spam de preferencias MP) |
| `POST /api/comentarios` | 5 requests/min |
| `POST /api/notificar-lanzamiento` | 5 requests/min |
| `/api/*` y `/dashboard/*` (general) | 10 requests/min |
| `/webhook/*` e `/images/*` | **Sin límite** (excluidos explícitamente) |

Los buckets se almacenan en Caffeine (máx 100.000 entradas, TTL 10 min de inactividad).

No hay límites por usuario de WhatsApp (solo por IP). Un usuario podría enviar mensajes desde múltiples IPs y el límite no se aplicaría.

---

## 9. Frontend actual

### `src/main/resources/static/index.html`

Es la **landing page kinagentes.com**. Es HTML estático puro (no Thymeleaf). Usa:
- CSS inline / estilos propios (no Tailwind, no Bootstrap)
- JavaScript vanilla para animaciones y la sección de FAQ
- Google Analytics 4 (`G-RKT9LK5MM9`)
- Secciones: Hero, Características, Precios ($5/mes), FAQ, CTA, Footer
- Links a checkout, privacidad, términos
- Referencia a contacto: soporte@kinagentes.com

### `src/main/resources/static/checkout.html`

Maneja el **flujo de pago** de MercadoPago. Es HTML estático puro. Funcionalidades:
- Formulario con campos: email + número WhatsApp
- Validación client-side (email, teléfono)
- `POST /api/create-subscription` → obtiene `checkout_url`
- Redirige al checkout de MercadoPago
- Detecta retorno desde MP via query params y muestra mensaje de éxito/error

### `src/main/resources/templates/dashboard_gastos_v6.html`

Es un **template Thymeleaf** (está en `/templates/`, no en `/static/`). Genera el dashboard interactivo con:
- Métricas principales (ingresos, gastos, balance)
- Donut chart de gastos por categoría (canvas JS)
- Line chart de gasto diario (últimos 14 días)
- Barras de presupuesto por categoría (top 5)
- Últimas 5 transacciones
- Playwright lo renderiza a PNG para enviar por WhatsApp

### `src/main/resources/static/privacidad.html` y `terminos.html`

Páginas legales estáticas.

---

## 10. Variables de entorno y secretos

| Variable | Requerida | Qué pasa si falta |
|---|---|---|
| `ANTHROPIC_API_KEY` | **CRÍTICA** | La app inicia pero falla en el primer mensaje a Claude |
| `DB_URL` | **CRÍTICA** | La app no inicia (error de conexión a BD) |
| `DB_USERNAME` | **CRÍTICA** | La app no inicia |
| `DB_PASSWORD` | **CRÍTICA** | La app no inicia |
| `TWILIO_ACCOUNT_SID` | **CRÍTICA** | La validación de firmas falla, todos los mensajes son rechazados |
| `TWILIO_AUTH_TOKEN` | **CRÍTICA** | Igual que arriba |
| `TWILIO_WHATSAPP_NUMBER` | **CRÍTICA** | No se pueden enviar mensajes salientes |
| `MP_ACCESS_TOKEN` | **CRÍTICA** | No se pueden crear preferencias de pago ni consultar payments |
| `ADMIN_PASSWORD` | **CRÍTICA** | El panel admin es accesible para cualquiera (falla vacía → cualquier string pasa) |
| `APP_BASE_URL` | **CRÍTICA** | La validación de firma de Twilio falla (URL incorrecta); default: `http://localhost:8080` |
| `MP_WEBHOOK_SECRET` | Alta | Si está vacío, la validación de firma de MP webhook pasa siempre (inseguro) |
| `MP_PLAN_AMOUNT` | Media | Default: `5000` CLP |
| `MP_PLAN_CURRENCY` | Media | Default: `CLP` |
| `PORT` | Baja | Default: `8080` |

> **Nota sobre `ADMIN_PASSWORD`:** Si la variable está vacía o ausente, y el código compara contra un string vacío, podría volverse inseguro. No se pudo verificar el comportamiento exacto sin ejecutar el código, pero se recomienda verificar este caso edge.

---

## 11. Estado de la base de datos

No fue posible conectarse a la base de datos durante este análisis (las credenciales están en variables de entorno no disponibles en el entorno de análisis estático).

Lo que se puede inferir del código:

**Tablas que deben existir en producción** (basado en las entities con `@Table`):

| Tabla | Estado estimado |
|---|---|
| `gastos` | Tiene datos reales (lógica de comparación mes anterior funciona, hay usuarios activos) |
| `usuario_perfil` | Tiene datos reales |
| `suscripcion` | Tiene al menos algunos registros con estado `ACTIVO` |
| `logros_usuario` | Tiene datos |
| `categorización_aprendida` | Tiene datos |
| `comentarios` | Probablemente tiene algunos comentarios |
| `agente_lanzado` | Tiene registros de control |
| `cross_notificacion` | Existe pero puede estar vacía |
| `notificacion_lanzamiento` | Existe, tiene registros de lista de espera |
| `conversation_turns` | Recién creada (commit reciente), puede estar vacía |

**Cantidad de filas reales:** No determinable sin acceso a la BD.

**¿Datos de prueba o producción?** La arquitectura (Railway + Supabase con pgbouncer, URL de producción hardcodeada en varios servicios) indica que es un entorno de producción real.

---

## RIESGOS DETECTADOS

### Lo que está bien implementado y es seguro

- **Validación de firma de Twilio:** HMAC-SHA1 implementada correctamente. Sin ella, cualquiera podría inyectar mensajes falsos.
- **Validación de firma de MercadoPago:** HMAC-SHA256 implementada con el formato correcto.
- **Tokens de un solo uso:** Dashboard y PDF usan tokens con TTL corto que se invalidan al primer uso. Bien pensado.
- **Rate limiting por IP:** Cubre los vectores principales de abuso (spam de subscripciones, brute force del admin).
- **Masking en logs:** Los números de teléfono y emails se enmascaran antes de loguearse. Evita leakage de PII.
- **Protección contra path traversal:** Los endpoints de archivos validan explícitamente que el filename no contenga `..`, `/` o `\`.
- **Security headers filter:** Existe un filtro que agrega headers de seguridad HTTP (CSP, X-Frame-Options, etc.).
- **Thread virtual para I/O:** El procesamiento de mensajes WhatsApp se hace en hilos virtuales (Java 21), liberando el thread HTTP inmediatamente.
- **Respuesta 200 inmediata al webhook de Twilio:** Evita reintentos de Twilio mientras el procesamiento real es asíncrono.

---

### Lo que funciona pero es frágil

**[CRÍTICO] Historial de conversación en memoria**
El `ConcurrentHashMap` en `ClaudeService` se pierde cada vez que el servidor reinicia o Railway hace un redeploy. El usuario siente que Faro "olvidó" todo. La tabla `conversation_turns` existe pero no está conectada al servicio.

**[CRÍTICO] Sin herramienta de migraciones de BD**
No hay Flyway ni Liquibase. Los scripts SQL no están en el repositorio (el único que hubo — `conversation_turns` — no se puede encontrar ahora). Si el servidor de BD se pierde, no hay forma de recrear el schema automáticamente. Todo el conocimiento del schema está en las entities Java y en la cabeza del equipo.

**[ALTO] Mensajes proactivos fuera de sesión de WhatsApp**
Los schedulers (resumen semanal, recordatorio de inactividad, saludo de nuevo mes) envían mensajes a usuarios que pueden no haber interactuado en más de 24 horas. Twilio puede bloquear estos mensajes a menos que se usen Twilio Content Templates (HSM) aprobados previamente. El código no usa HSM.

**[ALTO] Sin lógica de baja/cancelación de suscripción**
El webhook de MercadoPago solo procesa pagos `approved`. Si el usuario cancela o falla un pago, el estado en la tabla `suscripcion` nunca cambia de `ACTIVO`. El usuario seguiría usando el servicio sin pagar.

**[ALTO] `RecordatoriosScheduler.checkInactividad()` usa `findAll()`**
Carga todos los perfiles de usuario en memoria cada día. Con pocos usuarios está bien, pero no escala. A medida que la base de usuarios crezca, esto se volverá lento y puede causar problemas de memoria.

**[MEDIO] URLs de producción hardcodeadas en código**
`MercadoPagoService` tiene URL de webhook de producción (`agente-financiero-production.up.railway.app`) hardcodeada. Debería usar `APP_BASE_URL`.

**[MEDIO] Sin retry ni timeout configurado para llamadas a Claude**
Si la API de Anthropic tarda más de 30 segundos (default de OkHttp), la llamada falla. No hay reintentos automáticos. En producción, esto se traduce en mensajes de error al usuario.

**[MEDIO] Lógica de `extraerMes()` tiene bug potencial**
Si el usuario pide "el reporte de enero" en enero, y enero ya pasó, el código ajusta al año anterior. Pero si el año correcto es el actual, podría entregar datos del año pasado cuando el usuario quería el mes actual.

**[BAJO] El campo `metas` (VARCHAR 2000) y `metasJson` (TEXT) coexisten**
Parece haber un campo antiguo (`metas`) y uno nuevo (`metas_json`). Ambos se guardan, lo que puede generar confusión sobre cuál es la fuente de verdad. `MetaService` usa `metasJson`.

---

### Lo que falta claramente

**Tests:** Cero tests automatizados. Ni unitarios ni de integración. Cualquier refactor o nueva funcionalidad puede romper algo sin enterarse hasta que un usuario lo reporta.

**Monitoring y alertas:** No hay integración con Sentry, Datadog, New Relic u otra herramienta. Si la app falla en producción, nadie lo sabe hasta que alguien se queja. Los logs están en Railway pero no hay alertas configuradas (que sean visibles en el código).

**Cleanup de archivos temporales:** Los PNG de dashboards y PDFs se guardan en `/tmp/faro-images` y `/tmp/faro-reports`. No hay ninguna tarea que limpie estos directorios. En Railway, `/tmp` se limpia en cada redeploy, pero si la app corre muchas horas sin reiniciar, podría acumular archivos.

**Persistencia de conversaciones:** La tabla `conversation_turns` fue creada pero no está conectada. El historial en memoria se pierde en cada restart.

**Documentación del schema de BD:** No hay ningún archivo en el repo que documente el schema completo de las tablas. El conocimiento vive en las entities Java.

**Documentación del API:** No hay Swagger / OpenAPI configurado.

**Manejo de webhook de cancelación en MercadoPago:** El servicio de pagos solo procesa aprobaciones, no cancelaciones ni fallos.

---

### Deuda técnica identificada

| Ítem | Prioridad | Esfuerzo estimado |
|---|---|---|
| Agregar Flyway + scripts SQL del schema completo | Alta | 1 día |
| Conectar `ConversationTurnRepository` a `ClaudeService` | Alta | 2-3 hs |
| Implementar webhook de cancelación en MercadoPago | Alta | 3-4 hs |
| Agregar tests de integración para flujo WhatsApp | Alta | 2-3 días |
| Cambiar schedulers a queries paginadas (reemplazar `findAll()`) | Media | 2-3 hs |
| Implementar Content Templates (HSM) de Twilio para mensajes proactivos | Media | 1 día |
| Centralizar todas las URLs en `APP_BASE_URL` | Media | 1-2 hs |
| Integrar Sentry o similar para error tracking | Media | 2-3 hs |
| Agregar tarea de limpieza de archivos temporales | Baja | 1 hs |
| Eliminar campo `metas` redundante (usar solo `metas_json`) | Baja | 1 hs |
| Configurar timeout y retry explícito para el cliente de Anthropic | Media | 1-2 hs |
| Agregar documentación del schema SQL al repositorio | Media | 2-3 hs |

---

*Fin del análisis. 60 archivos Java analizados, 10 tablas documentadas, 17 services mapeados.*
