# MercadoPago Webhooks — Referencia técnica

Aplica a todos los agentes de Kin (Faro, Brújula, y futuros). Cada agente tiene su propia
fila en `suscripcion` con su `agente` correspondiente, pero el flujo de webhook es idéntico.

---

## Cómo llega un webhook

MercadoPago hace un `POST /api/webhook/mercadopago` con:

```
Headers:
  x-signature:   ts=1715000000,v1=<hmac_sha256_hex>
  x-request-id:  <uuid>

Body (JSON):
{
  "type":   "<event_type>",
  "action": "<event_type>.<verb>",
  "data":   { "id": "<resource_id>" }
}
```

La firma HMAC-SHA256 se valida antes de procesar. El `data.id` es un ID de recurso —
hay que hacer una llamada a la API de MP para obtener los detalles completos.

---

## Tipos de evento

### 1. `payment` — Pago de preferencia (one-shot)

Dispatched when: el usuario completa (o falla) un pago de preferencia.

**Actions:**
| action | descripción |
|--------|-------------|
| `payment.created` | Pago registrado (puede estar pendiente) |
| `payment.updated` | Estado del pago cambió |

**Cómo obtener detalles:**
```java
Payment payment = new PaymentClient().get(Long.parseLong(dataId));
```

**Campos relevantes del objeto `Payment`:**

| campo | tipo | descripción |
|-------|------|-------------|
| `status` | String | Estado actual (ver tabla abajo) |
| `external_reference` | String | Número WhatsApp del usuario (ej. `56912345678`) |
| `id` | Long | ID del pago en MP |
| `transaction_amount` | BigDecimal | Monto cobrado |
| `payer.email` | String | Email del pagador |

**Valores de `status` y acción requerida:**

| status | significado | acción en Kin |
|--------|-------------|---------------|
| `approved` | ✅ Pago exitoso | PENDIENTE → ACTIVO; crear perfil; enviar bienvenida |
| `pending` | ⏳ En proceso (transferencia, efectivo) | No hacer nada; esperar `payment.updated` |
| `in_process` | ⏳ En revisión antifraude | No hacer nada; esperar `payment.updated` |
| `rejected` | ❌ Rechazado por el banco | Log; no cambiar estado (usuario puede reintentar) |
| `cancelled` | ❌ Expiró sin pago | Log; dejar en PENDIENTE |
| `refunded` | 💸 Reembolso solicitado | ACTIVO → CANCELADO; notificar usuario por WA |
| `charged_back` | ⚠️ Contracargo / disputa | ACTIVO → CANCELADO; log con prioridad alta |

**Estado actual:** solo `approved` está implementado.

---

### 2. `subscription_preapproval` — Estado de la suscripción recurrente

Dispatched when: el usuario cancela, pausa, o reactiva su suscripción recurrente desde
el portal de MP o vía API.

> **Nota:** En el flujo actual de Kin se usa `PreferenceClient` (cobro one-shot), no
> suscripciones recurrentes de MP. Si en el futuro se migra a cobro recurrente con
> `PreApprovalClient`, estos eventos serán la fuente principal de verdad del estado.

**Actions:**
| action | descripción |
|--------|-------------|
| `subscription_preapproval.created` | Nueva suscripción recurrente creada |
| `subscription_preapproval.updated` | Estado cambió |

**Cómo obtener detalles:**
```java
// SDK Java: com.mercadopago.client.preapproval.PreApprovalClient
PreApproval preApproval = new PreApprovalClient().get(dataId);
```

**Campos relevantes:**

| campo | tipo | descripción |
|-------|------|-------------|
| `status` | String | Estado de la suscripción |
| `external_reference` | String | Número WhatsApp del usuario |
| `next_payment_date` | OffsetDateTime | Próximo cobro programado |
| `reason` | String | Descripción del plan |

**Valores de `status` y acción requerida:**

| status | significado | acción en Kin |
|--------|-------------|---------------|
| `authorized` | ✅ Activa y cobrando | CANCELADO/VENCIDO → ACTIVO; notificar reactivación |
| `paused` | ⏸ Pausada por el usuario | ACTIVO → VENCIDO; notificar pausa (acceso reducido) |
| `cancelled` | ❌ Cancelada definitivamente | ACTIVO → CANCELADO; notificar cancelación; despedida |

---

### 3. `subscription_authorized_payment` — Cobro recurrente individual

Dispatched when: MP intenta (o completa) un cobro automático de renovación mensual.

> Aplica solo si se usa `PreApprovalClient`. Con el flujo actual one-shot, los cobros
> recurrentes los procesa el propio usuario volviendo a pagar.

**Actions:**
| action | descripción |
|--------|-------------|
| `subscription_authorized_payment.created` | Cobro programado creado |
| `subscription_authorized_payment.updated` | Estado del cobro cambió |

**Cómo obtener detalles:**
```java
// GET https://api.mercadopago.com/authorized_payments/{id}
// No hay clase client en SDK Java 2.x — usar RestTemplate o HttpClient directamente
```

**Valores de `status` y acción requerida:**

| status | significado | acción en Kin |
|--------|-------------|---------------|
| `authorized` | ⏳ Cobro programado, no ejecutado aún | No hacer nada |
| `processed` | ✅ Cobro exitoso | Actualizar `fecha_proximo_pago`; confirmar al usuario |
| `recycling` | 🔄 Falló, MP reintentará (hasta 4 reintentos) | Notificar usuario: "problema con tu pago" |
| `cancelled` | ❌ Todos los reintentos fallaron | ACTIVO → CANCELADO; notificar; acceso cortado |

---

## Idempotencia

MP puede enviar el mismo evento múltiples veces (reintentos por timeout). Para evitar
duplicados, guardar `data.id` en una tabla de eventos procesados y hacer `INSERT IGNORE`
antes de procesar.

```sql
-- Propuesta: tabla suscripcion_cambios (ver V3__add_subscription_audit.sql)
-- Columna: mp_event_id VARCHAR(100) UNIQUE
```

Antes de procesar cualquier evento:
1. Buscar `mp_event_id` en la tabla
2. Si ya existe → return inmediato (responder 200 a MP)
3. Si no existe → procesar y guardar el evento

---

## Mensajes WhatsApp por estado

| evento | mensaje sugerido |
|--------|-----------------|
| Pago aprobado | Mensaje de bienvenida completo (ya implementado) |
| Contracargo | "Detectamos una disputa en tu pago. Tu acceso a Faro fue suspendido temporalmente. Contáctanos en soporte@kinagentes.com" |
| Reembolso | "Tu reembolso fue procesado. Tu acceso ha sido desactivado. ¡Esperamos verte pronto!" |
| Cancelación | "Tu suscripción a Faro fue cancelada. Puedes volver cuando quieras en kinagentes.com/faro ⭐" |
| Pausa | "Tu suscripción está pausada. Tu acceso está temporalmente inactivo." |
| Pago fallido (recycling) | "Tuvimos un problema al renovar tu suscripción. Revisaremos el cobro automáticamente. Si persiste, escríbenos a soporte@kinagentes.com" |
| Cobro fallido definitivo | "No pudimos procesar tu pago de renovación. Tu acceso fue suspendido. Renueva en kinagentes.com/faro" |
| Reactivación | "¡Bienvenido de vuelta! Tu suscripción a Faro está activa nuevamente. ¡Sigamos con tus finanzas! 💪" |

---

## Flujo de estados de `suscripcion.estado`

```
PENDIENTE ──(payment approved)──────────────────► ACTIVO
             (subscription authorized)                │
                                                      │
          ◄──(payment refunded/charged_back)──────────┤
CANCELADO ◄──(preapproval cancelled)─────────────────┤
          ◄──(authorized_payment cancelled)───────────┤
                                                      │
VENCIDO   ◄──(preapproval paused)────────────────────┘
    │
    └──(preapproval authorized)──────────────────► ACTIVO
```

---

## Verificación en sandbox

1. Activar cuenta de prueba en [developers.mercadopago.com](https://www.mercadopago.com.ar/developers/panel)
2. Exponer endpoint local con ngrok: `ngrok http 8080`
3. Configurar webhook URL en MP Developers → Tu aplicación → Webhooks de prueba
4. Usar tarjetas de prueba de MP para simular aprobados/rechazados
5. Simular cancelación desde el portal de test del pagador

---

## Referencias

- [Notificaciones webhooks — MP Devs](https://www.mercadopago.com.ar/developers/es/docs/your-integrations/notifications/webhooks)
- [Suscripciones recurrentes — MP Devs](https://www.mercadopago.com.ar/developers/es/docs/subscriptions/landing)
- [Tarjetas de prueba por país](https://www.mercadopago.com.ar/developers/es/docs/your-integrations/test/cards)
