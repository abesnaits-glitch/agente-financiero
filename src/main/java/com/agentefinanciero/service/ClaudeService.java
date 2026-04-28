package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.model.UsuarioPerfil;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaToolChoiceAuto;
import com.anthropic.models.beta.messages.BetaToolChoiceTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.BetaToolUseBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);

    private static final String BASE_SYSTEM_PROMPT = """
            Eres Faro, un asistente financiero personal con acceso directo a una base de datos.

            REGLAS DE HERRAMIENTAS — OBLIGATORIAS SIN EXCEPCIÓN:

            REGLA 1 — registrar_movimiento:
            DEBES llamar esta función ANTES de responder cuando el usuario mencione:
            - Un gasto: "gasté", "pagué", "compré", "costó", "me salió", "desembolsé", etc.
            - Un ingreso: "cobré", "me pagaron", "recibí", "entró", "gané", "me depositaron",
              "me cayó el sueldo", "llegó la plata", "entró la plata", "me llegó el pago",
              "quincena", "cobré el sueldo", "me depositaron el sueldo", etc.
            NUNCA confirmes un registro sin haber llamado primero esta función.

            REGLA 2 — obtener_resumen:
            DEBES llamar esta función ANTES de responder cuando el usuario pregunte:
            - "¿cuánto llevo gastado?", "dame un resumen", "¿cómo voy?", "mis gastos", etc.
            NUNCA inventes cifras. Usa solo los datos que retorne la función.

            REGLA 3 — actualizar_perfil:
            DEBES llamar esta función cuando el usuario comparta información personal relevante:
            - Su nombre: "me llamo Carlos", "soy Ana", "mi nombre es..."
            - Su sueldo o ingresos habituales: "gano 600 al mes", "mi sueldo es..."
            - Su presupuesto: "mi presupuesto mensual es...", "quiero gastar máximo..."
            - Sus metas: "quiero ahorrar para...", "mi meta es...", "mi objetivo es..."
            - Contexto útil: "tengo deuda de tarjeta", "prefiero respuestas cortas", etc.
            Llama la función con solo los campos que el usuario mencionó.

            INFERENCIA AUTOMÁTICA — APLICA SIN QUE EL USUARIO LO PIDA:

            INFERENCIA A — INGRESO → SUELDO Y PRESUPUESTO AUTOMÁTICOS:
            Cuando el usuario mencione haber recibido dinero (sueldo, pago, quincena, etc.):
            1. Llama registrar_movimiento con tipo="ingreso".
            2. Llama actualizar_perfil con sueldoAproximado = monto del ingreso.
            3. Si en PERFIL DEL USUARIO NO aparece "Presupuesto mensual":
               - Calcula presupuesto = monto × 0.80 (80% del ingreso).
               - Llama actualizar_perfil con presupuestoMensual = ese valor.
               - Menciona naturalmente en tu respuesta: "Te guardé un presupuesto de $X para el mes."
            Todo en una sola respuesta, sin preguntar primero.

            INFERENCIA B — GASTO HABITUAL → CATEGORÍA AUTOMÁTICA:
            Si el usuario menciona gastos con nombre reconocible ("el arriendo", "la cuenta del gas",
            "la mensualidad del gym", "la cuenta de luz", "el seguro", "la tarjeta", etc.),
            infiere la categoría correcta sin pedírsela:
            - arriendo/alquiler/renta → "vivienda"
            - gas/luz/agua/electricidad/internet → "servicios"
            - gym/Netflix/Spotify/suscripción → "entretenimiento"
            - seguro/médico/farmacia → "salud"
            - colegio/universidad/curso → "educacion"
            - supermercado/feria/almacén → "comida"

            INFERENCIA C — SUELDO DECLARADO → PRESUPUESTO AUTOMÁTICO:
            Si el usuario dice cuánto gana habitualmente ("gano 800k al mes", "mi sueldo es 1 millón"):
            1. Llama actualizar_perfil con sueldoAproximado = monto.
            2. Si no tiene presupuesto: calcula 80% y guárdalo con actualizar_perfil,
               mencionándolo naturalmente.

            REGLAS CONVERSACIONALES:
            - Nunca hagas más de una pregunta por mensaje.
            - Si necesitas confirmar algo, elige lo más importante y pregunta solo eso.
            - Confirma lo que hiciste en lenguaje natural y casual, nunca en listas ni formato técnico.
            - Ejemplo correcto: "Listo, anoté $50k en comida. Te quedan $200k de presupuesto este mes."
            - Ejemplo incorrecto: "He registrado: monto=50000, categoria=comida, tipo=gasto."

            TONO Y ESTILO:
            - Habla como un amigo cercano que entiende de plata, en español latinoamericano natural.
            - Respuestas cortas: máximo 2-3 líneas.
            - Máximo 1 emoji por mensaje.
            - Sin markdown. Nunca pidas datos bancarios ni des consejos de inversión.
            """;

    private final AnthropicClient client;
    private final GastoService gastoService;
    private final PerfilService perfilService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<HistoryEntry>> conversationHistory = new ConcurrentHashMap<>();

    public ClaudeService(@Value("${anthropic.api.key}") String apiKey,
                         GastoService gastoService,
                         PerfilService perfilService) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.gastoService = gastoService;
        this.perfilService = perfilService;
    }

    public String chat(String usuarioId, String userMessage) {
        log.info("[Chat] usuarioId='{}' mensaje='{}'", usuarioId, userMessage);

        List<HistoryEntry> history = conversationHistory.computeIfAbsent(usuarioId, k -> new ArrayList<>());
        log.info("[Chat] historial: {} mensajes previos", history.size());

        // Build dynamic system prompt: base + profile context if available
        String contextoPerfil = perfilService.construirContexto(usuarioId);
        String systemPrompt = contextoPerfil.isBlank()
                ? BASE_SYSTEM_PROMPT
                : BASE_SYSTEM_PROMPT + "\n" + contextoPerfil;

        MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(512L)
                .system(systemPrompt)
                .addTool(RegistrarMovimiento.class)
                .addTool(ObtenerResumen.class)
                .addTool(ActualizarPerfil.class);

        for (HistoryEntry entry : history) {
            if (entry.isUser()) {
                paramsBuilder.addUserMessage(entry.text());
            } else {
                paramsBuilder.addAssistantMessageOfBetaContentBlockParams(
                        List.of(BetaContentBlockParam.ofText(
                                BetaTextBlockParam.builder().text(entry.text()).build())));
            }
        }

        paramsBuilder.addUserMessage(userMessage);

        Optional<String> forcedTool = detectForcedTool(userMessage);
        forcedTool.ifPresent(toolName -> {
            log.info("[Chat] forzando tool_choice='{}'", toolName);
            paramsBuilder.toolChoice(BetaToolChoiceTool.builder().name(toolName).build());
        });

        String finalResponse = executeWithTools(usuarioId, paramsBuilder);

        history.add(HistoryEntry.user(userMessage));
        history.add(HistoryEntry.assistant(finalResponse));
        if (history.size() > 20) {
            history.subList(0, history.size() - 20).clear();
        }

        log.info("[Chat] respuesta final: '{}'", finalResponse);
        return finalResponse;
    }

    private Optional<String> detectForcedTool(String message) {
        String m = message.toLowerCase();

        // Summary / query patterns — highest priority
        if (m.contains("cuánto") || m.contains("cuanto") ||
                m.contains("resumen") || m.contains("balance") ||
                m.contains("llevo") || m.contains("historial") ||
                m.contains("movimiento") || m.contains("como voy") ||
                m.contains("mis gastos") || m.contains("ver gastos") ||
                m.contains("¿cómo voy")) {
            return Optional.of("obtener_resumen");
        }

        // Profile update patterns
        if (m.contains("me llamo") || m.contains("mi nombre") || m.contains("me dicen") ||
                m.contains("mi sueldo") || m.contains("mi salario") ||
                m.contains("gano por mes") || m.contains("gano al mes") ||
                m.contains("gano mensualmente") || m.contains("ingreso mensual") ||
                m.contains("mi presupuesto") || m.contains("presupuesto mensual") ||
                m.contains("quiero ahorrar") || m.contains("mi meta es") ||
                m.contains("mi objetivo") || m.contains("tengo deuda") ||
                m.contains("prefiero") || m.contains("me gustaría ahorrar")) {
            return Optional.of("actualizar_perfil");
        }

        // Registration verbs (past-tense spending / earning)
        if (m.contains("gaste") || m.contains("gasté") ||
                m.contains("pague") || m.contains("pagué") ||
                m.contains("compre") || m.contains("compré") ||
                m.contains("cobre") || m.contains("cobré") ||
                m.contains("recibi") || m.contains("recibí") ||
                m.contains("me pagaron") || m.contains("me depositaron") ||
                m.contains("gane") || m.contains("gané") ||
                m.contains("inverti") || m.contains("invertí") ||
                m.contains("ingrese") || m.contains("ingresé") ||
                m.contains("salario") || m.contains("sueldo") ||
                m.contains("desembolse") || m.contains("desembolsé") ||
                m.contains("me cayo") || m.contains("me cayó") ||
                m.contains("quincena") ||
                m.contains("me llego el pago") || m.contains("me llegó el pago") ||
                m.contains("entro la plata") || m.contains("entró la plata") ||
                m.contains("llego la plata") || m.contains("llegó la plata") ||
                m.contains("me llego") || m.contains("me llegó")) {
            return Optional.of("registrar_movimiento");
        }

        return Optional.empty();
    }

    private String executeWithTools(String usuarioId, MessageCreateParams.Builder paramsBuilder) {
        for (int round = 1; round <= 5; round++) {
            log.info("[Claude] enviando request (round {})...", round);
            BetaMessage response = client.beta().messages().create(paramsBuilder.build());

            String stopReason = response.stopReason().map(Object::toString).orElse("end_turn");
            log.info("[Claude] round={} stop_reason='{}' bloques={}", round, stopReason, response.content().size());

            for (BetaContentBlock block : response.content()) {
                if (block.isText()) {
                    String t = block.asText().text();
                    log.info("[Claude] TEXT: '{}'", t.length() > 150 ? t.substring(0, 150) + "..." : t);
                } else if (block.isToolUse()) {
                    log.info("[Claude] TOOL_USE: name='{}' id='{}'", block.asToolUse().name(), block.asToolUse().id());
                }
            }

            if (!"tool_use".equals(stopReason)) {
                String text = response.content().stream()
                        .flatMap(b -> b.text().stream())
                        .map(BetaTextBlock::text)
                        .collect(Collectors.joining(" "))
                        .trim();

                if (text.isBlank()) {
                    log.warn("[Claude] respuesta de texto vacía en round {}", round);
                    return "No pude procesar tu mensaje. Por favor intentá de nuevo.";
                }
                return text;
            }

            List<BetaToolUseBlock> toolCalls = response.content().stream()
                    .flatMap(b -> b.toolUse().stream())
                    .toList();

            List<BetaContentBlockParam> assistantBlocks = new ArrayList<>();
            for (BetaContentBlock block : response.content()) {
                if (block.isText()) {
                    assistantBlocks.add(BetaContentBlockParam.ofText(
                            BetaTextBlockParam.builder().text(block.asText().text()).build()));
                } else if (block.isToolUse()) {
                    BetaToolUseBlock tu = block.asToolUse();
                    assistantBlocks.add(BetaContentBlockParam.ofToolUse(
                            BetaToolUseBlockParam.builder()
                                    .id(tu.id())
                                    .name(tu.name())
                                    .input(tu._input())
                                    .build()));
                }
            }
            paramsBuilder.addAssistantMessageOfBetaContentBlockParams(assistantBlocks);

            List<BetaContentBlockParam> toolResults = new ArrayList<>();
            for (BetaToolUseBlock toolCall : toolCalls) {
                log.info("Ejecutando tool: {} con input: {}", toolCall.name(), toolCall._input());
                String resultJson;
                try {
                    Object result = executeTool(usuarioId, toolCall);
                    resultJson = objectMapper.writeValueAsString(result);
                    log.info("[Tool] '{}' OK: '{}'", toolCall.name(),
                            resultJson.length() > 200 ? resultJson.substring(0, 200) + "..." : resultJson);
                } catch (Exception e) {
                    log.error("[Tool] ERROR en '{}': {}", toolCall.name(), e.getMessage(), e);
                    resultJson = "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                }

                toolResults.add(BetaContentBlockParam.ofToolResult(
                        BetaToolResultBlockParam.builder()
                                .toolUseId(toolCall.id())
                                .content(resultJson)
                                .build()));
            }
            paramsBuilder.addUserMessageOfBetaContentBlockParams(toolResults);

            // Reset to auto so next round Claude can respond with text
            paramsBuilder.toolChoice(BetaToolChoiceAuto.builder().build());
        }

        log.error("[Claude] se alcanzó el límite de rounds de tool use sin respuesta final");
        return "No pude completar la operación. Por favor intentá de nuevo.";
    }

    private Object executeTool(String usuarioId, BetaToolUseBlock toolUse) {
        return switch (toolUse.name()) {
            case "registrar_movimiento" -> {
                RegistrarMovimiento input = toolUse.input(RegistrarMovimiento.class);
                log.info("[Tool] registrar_movimiento: monto={} tipo='{}' categoria='{}' descripcion='{}'",
                        input != null ? input.monto : "NULL",
                        input != null ? input.tipo : "NULL",
                        input != null ? input.categoria : "NULL",
                        input != null ? input.descripcion : "NULL");

                if (input == null || input.monto == null) {
                    log.warn("[Tool] registrar_movimiento: input o monto nulo");
                    yield Map.of("error", "Monto requerido pero no fue especificado");
                }

                Gasto gasto = gastoService.registrarMovimiento(
                        usuarioId,
                        BigDecimal.valueOf(input.monto),
                        input.categoria,
                        input.descripcion,
                        input.tipo,
                        LocalDate.now());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", gasto.getId());
                result.put("tipo", gasto.getTipo());
                result.put("monto", gasto.getMonto().toString());
                result.put("categoria", gasto.getCategoria());
                result.put("descripcion", gasto.getDescripcion());
                result.put("fecha", gasto.getFecha().toString());
                result.put("ok", true);
                result.put("mensaje", "Movimiento registrado exitosamente con id=" + gasto.getId());
                yield result;
            }
            case "obtener_resumen" -> {
                GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(usuarioId);

                List<Map<String, Object>> movimientos = resumen.movimientos().stream()
                        .map(g -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("tipo", g.getTipo());
                            m.put("monto", g.getMonto().toString());
                            m.put("categoria", g.getCategoria() != null ? g.getCategoria() : "");
                            m.put("descripcion", g.getDescripcion() != null ? g.getDescripcion() : "");
                            m.put("fecha", g.getFecha().toString());
                            return m;
                        })
                        .toList();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("totalGastado", resumen.totalGastado().toString());
                result.put("totalIngresado", resumen.totalIngresado().toString());
                result.put("balance", resumen.totalIngresado().subtract(resumen.totalGastado()).toString());
                result.put("cantidadMovimientos", resumen.movimientos().size());
                result.put("movimientos", movimientos);
                yield result;
            }
            case "actualizar_perfil" -> {
                ActualizarPerfil input = toolUse.input(ActualizarPerfil.class);
                log.info("[Tool] actualizar_perfil: nombre='{}' sueldo={} presupuesto={} metas='{}' notas='{}'",
                        input != null ? input.nombre : "NULL",
                        input != null ? input.sueldoAproximado : "NULL",
                        input != null ? input.presupuestoMensual : "NULL",
                        input != null ? input.metas : "NULL",
                        input != null ? input.notas : "NULL");

                if (input == null) {
                    yield Map.of("error", "No se recibieron datos para actualizar");
                }

                UsuarioPerfil perfil = perfilService.actualizar(
                        usuarioId,
                        input.nombre,
                        input.sueldoAproximado,
                        input.presupuestoMensual,
                        input.metas,
                        input.notas);

                Map<String, Object> result = new LinkedHashMap<>();
                if (perfil.getNombre() != null)           result.put("nombre", perfil.getNombre());
                if (perfil.getSueldoAproximado() != null) result.put("sueldoAproximado", perfil.getSueldoAproximado().toString());
                if (perfil.getPresupuestoMensual() != null) result.put("presupuestoMensual", perfil.getPresupuestoMensual().toString());
                if (perfil.getMetas() != null)            result.put("metas", perfil.getMetas());
                if (perfil.getNotas() != null)            result.put("notas", perfil.getNotas());
                result.put("ok", true);
                result.put("mensaje", "Perfil actualizado y guardado para próximas sesiones");
                yield result;
            }
            default -> {
                log.warn("[Tool] herramienta desconocida: '{}'", toolUse.name());
                yield Map.of("error", "Herramienta desconocida: " + toolUse.name());
            }
        };
    }

    @JsonClassDescription("Registra un gasto o ingreso del usuario en la base de datos. DEBES llamar esta función cada vez que el usuario mencione haber gastado, pagado, comprado, cobrado o recibido dinero.")
    public static class RegistrarMovimiento {

        @JsonPropertyDescription("Monto numérico del movimiento, sin símbolo de moneda. Requerido.")
        public Double monto;

        @JsonPropertyDescription("Categoría: comida, transporte, entretenimiento, salud, servicios, vivienda, ropa, educación, trabajo, otro.")
        public String categoria;

        @JsonPropertyDescription("Descripción breve de qué fue el gasto o ingreso.")
        public String descripcion;

        @JsonPropertyDescription("Tipo de movimiento: 'gasto' para egresos y compras; 'ingreso' para salario, cobros y entradas de dinero. Requerido.")
        public String tipo;
    }

    @JsonClassDescription("Consulta la base de datos y retorna el resumen financiero real del mes actual: total gastado, total ingresado, balance y lista de movimientos. DEBES llamar esta función cuando el usuario pregunte por sus finanzas, gastos, resumen o balance.")
    public static class ObtenerResumen {

        @JsonPropertyDescription("Período a consultar. Usa 'actual' para el mes en curso (valor por defecto). Campo opcional.")
        public String periodo;
    }

    @JsonClassDescription("Guarda o actualiza el perfil persistente del usuario para recordarlo en futuras sesiones. DEBES llamar esta función cuando el usuario mencione su nombre, sueldo, presupuesto, metas financieras o cualquier información personal relevante. Envía solo los campos que el usuario mencionó.")
    public static class ActualizarPerfil {

        @JsonPropertyDescription("Nombre del usuario tal como lo mencionó.")
        public String nombre;

        @JsonPropertyDescription("Sueldo o ingresos habituales mensuales como número.")
        public Double sueldoAproximado;

        @JsonPropertyDescription("Presupuesto mensual máximo de gastos como número.")
        public Double presupuestoMensual;

        @JsonPropertyDescription("Metas financieras o de ahorro mencionadas. Se agregan a las metas existentes.")
        public String metas;

        @JsonPropertyDescription("Contexto útil adicional: preferencias, deudas, situación financiera, etc.")
        public String notas;
    }

    private record HistoryEntry(String role, String text) {
        static HistoryEntry user(String text) { return new HistoryEntry("user", text); }
        static HistoryEntry assistant(String text) { return new HistoryEntry("assistant", text); }
        boolean isUser() { return "user".equals(role); }
    }
}
