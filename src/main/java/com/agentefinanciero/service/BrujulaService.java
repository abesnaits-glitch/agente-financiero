package com.agentefinanciero.service;

import com.agentefinanciero.model.BrujulaAnalisis;
import com.agentefinanciero.model.BrujulaCheckin;
import com.agentefinanciero.model.BrujulaCuota;
import com.agentefinanciero.model.BrujulaProyecto;
import com.agentefinanciero.model.BrujulaRequest;
import com.agentefinanciero.repository.BrujulaAnalisisRepository;
import com.agentefinanciero.repository.BrujulaCheckinRepository;
import com.agentefinanciero.repository.BrujulaCuotaRepository;
import com.agentefinanciero.repository.BrujulaProyectoRepository;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BrujulaService {

    private static final Logger log = LoggerFactory.getLogger(BrujulaService.class);

    private static final int CUOTA_FREE     = 1;
    private static final int CUOTA_ESENCIAL = 5;

    // PDF colour palette (dark brand + verde lima accent)
    private static final Color DARK_BG  = new Color(20,  20,  20);
    private static final Color ACCENT   = new Color(0,  210, 130);
    private static final Color C_GREEN  = new Color(0,  210, 130);
    private static final Color C_RED    = new Color(215,  80,  80);
    private static final Color C_ORANGE = new Color(230, 140,  40);
    private static final Color TXT_DARK = new Color(30,  30,  30);
    private static final Color TXT_MID  = new Color(90,  90, 110);
    private static final Color TXT_LITE = new Color(160, 160, 180);
    private static final Color BORDER   = new Color(218, 218, 232);
    private static final Color ROW_ALT  = new Color(245, 245, 252);

    private static final String SYSTEM_PROMPT = """
            Eres Brújula, consultor experto en emprendimiento y desarrollo de negocios en Chile y LATAM, parte de la familia Kin de agentes de IA. Tienes experiencia profunda en evaluación de proyectos, análisis de mercado, modelos de negocio (Lean Canvas, Business Model Canvas), finanzas para emprendedores, y conocimiento detallado del ecosistema chileno: Corfo, Sercotec, INDAP, SII, banca pyme, FOGAPE, incubadoras universitarias.

            TU PERSONALIDAD:
            - Combinas pensamiento crítico con visión práctica
            - Eres honesto y directo, pero respetuoso. No infles expectativas.
            - Distingues claramente entre datos, supuestos y opiniones
            - Si una idea no es viable o el usuario no tiene los recursos, lo dices con respeto pero claridad
            - No inventas números: si no tienes datos, indicas rangos razonables y sugieres fuentes
            - Hablas con tono cercano pero profesional, en español de Chile

            CONTEXTO CHILENO QUE DOMINAS:
            - Programas de financiamiento: Capital Semilla Sercotec ($3,5M aprox), Corfo Semilla Inicia (hasta $25M), Corfo Crece, FOGAPE (garantías), INDAP PRODESAL/PADIS (rural), incubadoras universitarias (UDD Ventures, OpenBeauchef, Incuba UC), Startup Chile
            - Estructuras legales: persona natural con inicio actividades, EIRL, SpA, sociedad limitada
            - Regímenes tributarios: Pro Pyme Transparente, Pro Pyme General, régimen general
            - Normativa: patentes municipales, resoluciones sanitarias SEREMI, registro INAPI
            - Realidad económica chilena: poder adquisitivo, hábitos de consumo, e-commerce en crecimiento, alta competencia en delivery/servicios digitales básicos

            REGLA CRÍTICA SOBRE LA META DEL USUARIO:
            La meta del usuario debe MODULAR todo tu análisis. Si la meta es irreal para los recursos disponibles, dilo con respeto y propone: a) extender el plazo, b) sumar recursos, c) ajustar la meta, d) cambiar de modelo.
            NO recomiendes estrategias que la persona dijo que no está dispuesta a hacer.

            ESTRUCTURA DE RESPUESTA - devuelve SOLO JSON válido, sin markdown fences:
            {
              "veredicto": {
                "tipo": "viable|viable_ajustes|requiere_recursos|poco_viable|no_recomendable",
                "texto": "2-3 oraciones explicando el veredicto"
              },
              "comprension": {
                "resumen": "Cómo entendiste la idea en tus palabras",
                "modelo_negocio": "producto|servicio|suscripcion|marketplace|retail|ecommerce|otro"
              },
              "factibilidad": {
                "mercado": {
                  "puntaje": 1,
                  "analisis": "Texto del análisis de mercado",
                  "competencia": ["competidor1", "competidor2"],
                  "diferenciacion": "Cómo diferenciarse"
                },
                "operacional": {
                  "puntaje": 1,
                  "analisis": "Texto",
                  "permisos": ["permiso1", "permiso2"],
                  "proveedores_clave": ["proveedor1"]
                },
                "financiera": {
                  "puntaje": 1,
                  "inversion_inicial_min": 0,
                  "inversion_inicial_max": 0,
                  "costos_fijos_mes": 0,
                  "ingresos_conservador": 0,
                  "ingresos_realista": 0,
                  "ingresos_optimista": 0,
                  "punto_equilibrio_ventas": 0,
                  "payback_meses": 0,
                  "margen_esperado_pct": 0
                },
                "legal": {
                  "puntaje": 1,
                  "estructura_recomendada": "persona_natural|eirl|spa|ltda",
                  "regimen_tributario": "pro_pyme_transparente|pro_pyme_general|general",
                  "permisos_requeridos": ["patente municipal", "resolución sanitaria SEREMI"]
                }
              },
              "meta_vs_realidad": {
                "es_alcanzable": true,
                "explicacion": "Análisis honesto de si la meta es realista",
                "recomendacion_ajuste": "Si no es alcanzable, qué ajustar"
              },
              "hitos": [
                {"periodo": "3m", "meta_ingreso": 0, "acciones_criticas": ["acción1"], "kpis": ["kpi1"]},
                {"periodo": "6m", "meta_ingreso": 0, "acciones_criticas": ["acción1"], "kpis": ["kpi1"]},
                {"periodo": "12m", "meta_ingreso": 0, "acciones_criticas": ["acción1"], "kpis": ["kpi1"]},
                {"periodo": "24m", "meta_ingreso": 0, "acciones_criticas": ["acción1"], "kpis": ["kpi1"]}
              ],
              "que_se_necesita": {
                "financiamiento": [
                  {"programa": "Capital Semilla Sercotec", "monto": "$3.500.000 aprox", "link": "sercotec.cl"}
                ],
                "conocimientos": ["conocimiento1"],
                "validaciones": ["validación previa 1"],
                "alianzas": ["alianza estratégica 1"]
              },
              "alternativas": [
                {
                  "nombre": "Nombre de la alternativa",
                  "descripcion": "En qué consiste",
                  "por_que_mejor": "Por qué podría ser mejor",
                  "inversion_estimada": "$X - $Y CLP",
                  "dificultad": "baja|media|alta"
                }
              ],
              "plan_validacion_30_dias": {
                "semana_1": ["acción concreta 1", "acción concreta 2"],
                "semana_2": ["acción concreta 1"],
                "semana_3": ["acción concreta 1"],
                "semana_4": ["acción concreta 1"]
              },
              "proximos_pasos": ["paso 1", "paso 2", "paso 3"]
            }

            REGLAS DURAS:
            1. Cifras siempre en CLP o UF, nunca en USD para el mercado chileno
            2. Programas de financiamiento con monto y fuente para validar
            3. Para tributación, sugerir validar con contador o sii.cl
            4. No inventes estadísticas, usa rangos razonables
            5. El plan de hitos debe ser EJECUTABLE y CONCRETO
            6. Las alternativas deben respetar las restricciones del usuario
            """;

    private final AnthropicClient client;
    private final BrujulaAnalisisRepository analisisRepo;
    private final BrujulaProyectoRepository proyectoRepo;
    private final BrujulaCheckinRepository  checkinRepo;
    private final BrujulaCuotaRepository    cuotaRepo;
    private final TwilioService             twilioService;
    private final ObjectMapper              objectMapper = new ObjectMapper();

    public BrujulaService(@Value("${anthropic.api.key}") String apiKey,
                          BrujulaAnalisisRepository analisisRepo,
                          BrujulaProyectoRepository proyectoRepo,
                          BrujulaCheckinRepository  checkinRepo,
                          BrujulaCuotaRepository    cuotaRepo,
                          TwilioService             twilioService) {
        this.client        = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.analisisRepo  = analisisRepo;
        this.proyectoRepo  = proyectoRepo;
        this.checkinRepo   = checkinRepo;
        this.cuotaRepo     = cuotaRepo;
        this.twilioService = twilioService;
    }

    // ── Análisis principal ────────────────────────────────────────────────────

    @Transactional
    public BrujulaAnalisis generarAnalisis(BrujulaRequest request) {
        String usuarioId = resolverUsuarioId(request);
        log.info("[Brújula] iniciando análisis para usuario='{}' tipo='{}'",
                maskId(usuarioId), request.getTipoEvaluacion());

        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("[Brújula] error serializando request", e);
        }

        BrujulaAnalisis analisis = new BrujulaAnalisis();
        analisis.setUsuarioId(usuarioId);
        analisis.setEmail(request.getEmail());
        analisis.setTelefono(request.getTelefono());
        analisis.setTipoEvaluacion(request.getTipoEvaluacion());
        analisis.setInputData(inputJson);
        analisis.setEstado("pendiente");
        analisis.setPlan(request.getPlan() != null ? request.getPlan() : "free");
        analisis.setCreatedAt(LocalDateTime.now());
        analisis.setUpdatedAt(LocalDateTime.now());
        analisis = analisisRepo.save(analisis);

        try {
            String userMessage = buildUserMessage(request);
            log.info("[Brújula] llamando a Claude Opus para análisis id={}", analisis.getId());

            String rawResponse = client.messages().create(
                    MessageCreateParams.builder()
                            .model(Model.of("claude-opus-4-5"))
                            .maxTokens(4000L)
                            .system(SYSTEM_PROMPT)
                            .addUserMessage(userMessage)
                            .build()
            ).content().stream()
                    .filter(b -> b.isText())
                    .map(b -> b.asText().text())
                    .collect(Collectors.joining())
                    .trim();

            // Strip markdown fences if Claude added them despite the instruction
            String outputJson = stripMarkdownFences(rawResponse);
            log.info("[Brújula] respuesta recibida ({} chars)", outputJson.length());

            String veredicto = extraerVeredicto(outputJson);

            analisis.setOutputData(outputJson);
            analisis.setVeredicto(veredicto);
            analisis.setEstado("completado");
            analisis.setUpdatedAt(LocalDateTime.now());
            analisis = analisisRepo.save(analisis);

            registrarUso(usuarioId, analisis.getPlan());
            log.info("[Brújula] análisis id={} completado veredicto='{}'", analisis.getId(), veredicto);

        } catch (Exception e) {
            log.error("[Brújula] error generando análisis id={}: {}", analisis.getId(), e.getMessage(), e);
            analisis.setEstado("fallido");
            analisis.setUpdatedAt(LocalDateTime.now());
            analisisRepo.save(analisis);
            throw new RuntimeException("[Brújula] Error al generar el análisis: " + e.getMessage(), e);
        }

        return analisis;
    }

    // ── Control de cuotas ─────────────────────────────────────────────────────

    public boolean puedeAnalizar(String usuarioId, String plan) {
        String mes = mesActual();
        int usados = cuotaRepo.findByUsuarioIdAndMes(usuarioId, mes)
                .map(BrujulaCuota::getAnalisisCount)
                .orElse(0);

        return switch (plan != null ? plan.toLowerCase() : "free") {
            case "pro"      -> true;
            case "esencial" -> usados < CUOTA_ESENCIAL;
            default         -> usados < CUOTA_FREE;
        };
    }

    @Transactional
    public void registrarUso(String usuarioId, String plan) {
        String mes = mesActual();
        BrujulaCuota cuota = cuotaRepo.findByUsuarioIdAndMes(usuarioId, mes)
                .orElseGet(() -> {
                    BrujulaCuota c = new BrujulaCuota();
                    c.setUsuarioId(usuarioId);
                    c.setMes(mes);
                    c.setAnalisisCount(0);
                    c.setPlan(plan != null ? plan : "free");
                    return c;
                });
        cuota.setAnalisisCount(cuota.getAnalisisCount() + 1);
        cuotaRepo.save(cuota);
        log.info("[Brújula] uso registrado usuario='{}' mes={} total={}", maskId(usuarioId), mes, cuota.getAnalisisCount());
    }

    // ── Generación de PDF ─────────────────────────────────────────────────────

    public byte[] generarPdf(Long analisisId) {
        BrujulaAnalisis analisis = analisisRepo.findById(analisisId)
                .orElseThrow(() -> new RuntimeException("[Brújula] Análisis no encontrado: " + analisisId));

        if (!"completado".equals(analisis.getEstado()) || analisis.getOutputData() == null) {
            throw new RuntimeException("[Brújula] El análisis no está completado aún");
        }

        log.info("[Brújula] generando PDF para análisis id={}", analisisId);

        try {
            JsonNode json = objectMapper.readTree(analisis.getOutputData());
            return buildPdfBytes(analisis, json);
        } catch (Exception e) {
            log.error("[Brújula] error generando PDF id={}: {}", analisisId, e.getMessage(), e);
            throw new RuntimeException("[Brújula] Error generando PDF", e);
        }
    }

    private byte[] buildPdfBytes(BrujulaAnalisis analisis, JsonNode json) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 30, 50);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        writer.setPageEvent(new BrujulaFooterEvent());
        doc.open();

        try {
            Font fH18B = fnt(FontFactory.HELVETICA_BOLD,  18, Color.WHITE);
            Font fH13  = fnt(FontFactory.HELVETICA,       13, TXT_LITE);
            Font fH11B = fnt(FontFactory.HELVETICA_BOLD,  11, TXT_DARK);
            Font fH10  = fnt(FontFactory.HELVETICA,       10, TXT_LITE);
            Font fH9   = fnt(FontFactory.HELVETICA,        9, TXT_MID);
            Font fH9B  = fnt(FontFactory.HELVETICA_BOLD,   9, TXT_DARK);
            Font fH9W  = fnt(FontFactory.HELVETICA,        9, Color.WHITE);

            // ── Encabezado ──────────────────────────────────────────────────────
            PdfPTable hdr = new PdfPTable(new float[]{3, 1});
            hdr.setWidthPercentage(100);
            hdr.setSpacingAfter(14);

            PdfPCell hdrLeft = new PdfPCell();
            hdrLeft.setBackgroundColor(DARK_BG);
            hdrLeft.setPadding(16);
            hdrLeft.setBorder(Rectangle.NO_BORDER);
            Paragraph hdrTitle = new Paragraph();
            hdrTitle.add(new Chunk("KIN  ·  BRÚJULA  ", fH18B));
            hdrTitle.add(new Chunk("Evaluación de Negocio", fH13));
            hdrLeft.addElement(hdrTitle);
            hdrLeft.addElement(new Phrase("Análisis #" + analisis.getId() + "  ·  " +
                    analisis.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fH10));
            hdr.addCell(hdrLeft);

            PdfPCell hdrRight = new PdfPCell(new Phrase(
                    analisis.getTipoEvaluacion() != null
                            ? analisis.getTipoEvaluacion().replace("_", " ").toUpperCase() : "", fH10));
            hdrRight.setBackgroundColor(DARK_BG);
            hdrRight.setPadding(16);
            hdrRight.setBorder(Rectangle.NO_BORDER);
            hdrRight.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hdrRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
            hdr.addCell(hdrRight);
            doc.add(hdr);

            // ── Veredicto ───────────────────────────────────────────────────────
            JsonNode veredictoNode = json.path("veredicto");
            if (!veredictoNode.isMissingNode()) {
                sectionTitle(doc, "Veredicto", fH11B);
                String tipo  = veredictoNode.path("tipo").asText("—");
                String texto = veredictoNode.path("texto").asText("");
                Color badgeColor = veredictoColor(tipo);

                PdfPTable vTable = new PdfPTable(new float[]{1, 3});
                vTable.setWidthPercentage(100);
                vTable.setSpacingAfter(14);

                PdfPCell badgeCell = new PdfPCell(
                        new Phrase(tipo.replace("_", " ").toUpperCase(),
                                fnt(FontFactory.HELVETICA_BOLD, 11, Color.WHITE)));
                badgeCell.setBackgroundColor(badgeColor);
                badgeCell.setPadding(12);
                badgeCell.setBorder(Rectangle.NO_BORDER);
                badgeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                vTable.addCell(badgeCell);

                PdfPCell textoCell = new PdfPCell(new Phrase(texto, fH9));
                textoCell.setPadding(12);
                textoCell.setBorder(Rectangle.NO_BORDER);
                textoCell.setBackgroundColor(new Color(245, 245, 252));
                vTable.addCell(textoCell);
                doc.add(vTable);
            }

            // ── Comprensión ─────────────────────────────────────────────────────
            JsonNode comprension = json.path("comprension");
            if (!comprension.isMissingNode()) {
                sectionTitle(doc, "Comprensión de la Idea", fH11B);
                addTextBlock(doc, comprension.path("resumen").asText(""), fH9);
                String modelo = comprension.path("modelo_negocio").asText("");
                if (!modelo.isBlank()) {
                    addTextBlock(doc, "Modelo de negocio: " + modelo.replace("_", " "), fH9B);
                }
            }

            // ── Factibilidad ────────────────────────────────────────────────────
            JsonNode fact = json.path("factibilidad");
            if (!fact.isMissingNode()) {
                sectionTitle(doc, "Evaluación de Factibilidad", fH11B);

                // Puntajes resumidos en tabla
                PdfPTable scoreTable = new PdfPTable(4);
                scoreTable.setWidthPercentage(100);
                scoreTable.setSpacingAfter(10);
                addScoreCard(scoreTable, "Mercado",      fact.path("mercado").path("puntaje").asInt(0));
                addScoreCard(scoreTable, "Operacional",  fact.path("operacional").path("puntaje").asInt(0));
                addScoreCard(scoreTable, "Financiera",   fact.path("financiera").path("puntaje").asInt(0));
                addScoreCard(scoreTable, "Legal",        fact.path("legal").path("puntaje").asInt(0));
                doc.add(scoreTable);

                // Detalle financiero
                JsonNode fin = fact.path("financiera");
                if (!fin.isMissingNode()) {
                    sectionTitle(doc, "Proyección Financiera", fH11B);
                    PdfPTable finTable = new PdfPTable(new float[]{2, 2, 2, 2});
                    finTable.setWidthPercentage(100);
                    finTable.setSpacingAfter(14);
                    tblHeader(finTable, new String[]{"Inversión inicial", "Costos fijos/mes", "Ingresos realista", "Payback"});
                    String invMin = fmtClp(fin.path("inversion_inicial_min").asLong(0));
                    String invMax = fmtClp(fin.path("inversion_inicial_max").asLong(0));
                    tblRow(finTable, new String[]{
                            invMin + " – " + invMax,
                            fmtClp(fin.path("costos_fijos_mes").asLong(0)),
                            fmtClp(fin.path("ingresos_realista").asLong(0)),
                            fin.path("payback_meses").asInt(0) + " meses"
                    }, Color.WHITE, fH9);
                    doc.add(finTable);
                }

                // Análisis de mercado
                String analisisMercado = fact.path("mercado").path("analisis").asText("");
                if (!analisisMercado.isBlank()) {
                    sectionTitle(doc, "Análisis de Mercado", fH11B);
                    addTextBlock(doc, analisisMercado, fH9);
                }

                // Legal
                JsonNode legal = fact.path("legal");
                if (!legal.isMissingNode()) {
                    sectionTitle(doc, "Estructura Legal Recomendada", fH11B);
                    addTextBlock(doc, "Estructura: " + legal.path("estructura_recomendada").asText("").replace("_", " "), fH9B);
                    addTextBlock(doc, "Régimen tributario: " + legal.path("regimen_tributario").asText("").replace("_", " "), fH9);
                    JsonNode permisos = legal.path("permisos_requeridos");
                    if (permisos.isArray() && permisos.size() > 0) {
                        StringBuilder sb = new StringBuilder("Permisos requeridos: ");
                        for (int i = 0; i < permisos.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(permisos.get(i).asText());
                        }
                        addTextBlock(doc, sb.toString(), fH9);
                    }
                }
            }

            // ── Meta vs Realidad ────────────────────────────────────────────────
            JsonNode metaNode = json.path("meta_vs_realidad");
            if (!metaNode.isMissingNode()) {
                sectionTitle(doc, "Meta vs Realidad", fH11B);
                boolean alcanzable = metaNode.path("es_alcanzable").asBoolean(true);
                addTextBlock(doc, (alcanzable ? "✓ La meta es alcanzable." : "⚠ La meta requiere ajustes.") + " " +
                        metaNode.path("explicacion").asText(""), fH9);
                String ajuste = metaNode.path("recomendacion_ajuste").asText("");
                if (!ajuste.isBlank()) {
                    addTextBlock(doc, "Recomendación: " + ajuste, fH9);
                }
            }

            // ── Plan de hitos ───────────────────────────────────────────────────
            JsonNode hitos = json.path("hitos");
            if (hitos.isArray() && hitos.size() > 0) {
                sectionTitle(doc, "Plan de Hitos", fH11B);
                PdfPTable hitosTable = new PdfPTable(new float[]{1, 2, 3, 2});
                hitosTable.setWidthPercentage(100);
                hitosTable.setSpacingAfter(14);
                tblHeader(hitosTable, new String[]{"Período", "Meta ingreso", "Acciones críticas", "KPIs"});
                boolean alt = false;
                for (JsonNode hito : hitos) {
                    String acciones = joinArray(hito.path("acciones_criticas"), " / ");
                    String kpis     = joinArray(hito.path("kpis"), " / ");
                    Color bg = alt ? ROW_ALT : Color.WHITE;
                    tblRow(hitosTable, new String[]{
                            hito.path("periodo").asText(""),
                            fmtClp(hito.path("meta_ingreso").asLong(0)),
                            acciones,
                            kpis
                    }, bg, fH9);
                    alt = !alt;
                }
                doc.add(hitosTable);
            }

            // ── Alternativas ────────────────────────────────────────────────────
            JsonNode alternativas = json.path("alternativas");
            if (alternativas.isArray() && alternativas.size() > 0) {
                sectionTitle(doc, "Alternativas", fH11B);
                for (JsonNode alt : alternativas) {
                    PdfPTable altTable = new PdfPTable(1);
                    altTable.setWidthPercentage(100);
                    altTable.setSpacingAfter(8);
                    PdfPCell c = new PdfPCell();
                    c.setBackgroundColor(new Color(245, 244, 255));
                    c.setPaddingLeft(14); c.setPaddingRight(14);
                    c.setPaddingTop(10); c.setPaddingBottom(10);
                    c.setBorder(Rectangle.LEFT);
                    c.setBorderWidthLeft(3.5f);
                    c.setBorderColorLeft(ACCENT);
                    Paragraph p = new Paragraph();
                    p.add(new Chunk(alt.path("nombre").asText("") + "\n",
                            fnt(FontFactory.HELVETICA_BOLD, 10, TXT_DARK)));
                    p.add(new Chunk(alt.path("descripcion").asText("") + "\n", fH9));
                    p.add(new Chunk("Inversión: " + alt.path("inversion_estimada").asText("") +
                            "  ·  Dificultad: " + alt.path("dificultad").asText(""),
                            fnt(FontFactory.HELVETICA, 8, TXT_MID)));
                    c.addElement(p);
                    altTable.addCell(c);
                    doc.add(altTable);
                }
            }

            // ── Plan de validación 30 días ──────────────────────────────────────
            JsonNode plan30 = json.path("plan_validacion_30_dias");
            if (!plan30.isMissingNode()) {
                sectionTitle(doc, "Plan de Validación — 30 Días", fH11B);
                String[] semanas = {"semana_1", "semana_2", "semana_3", "semana_4"};
                String[] labels  = {"Semana 1", "Semana 2", "Semana 3", "Semana 4"};
                PdfPTable planTable = new PdfPTable(4);
                planTable.setWidthPercentage(100);
                planTable.setSpacingAfter(14);
                tblHeader(planTable, labels);
                PdfPCell[] cells = new PdfPCell[4];
                for (int i = 0; i < 4; i++) {
                    StringBuilder sb = new StringBuilder();
                    JsonNode semana = plan30.path(semanas[i]);
                    if (semana.isArray()) {
                        for (JsonNode item : semana) {
                            sb.append("• ").append(item.asText()).append("\n");
                        }
                    }
                    cells[i] = new PdfPCell(new Phrase(sb.toString().trim(), fH9));
                    cells[i].setPadding(8);
                    cells[i].setBorderColor(BORDER);
                    cells[i].setBorderWidth(0.5f);
                    planTable.addCell(cells[i]);
                }
                doc.add(planTable);
            }

            // ── Próximos pasos ──────────────────────────────────────────────────
            JsonNode pasos = json.path("proximos_pasos");
            if (pasos.isArray() && pasos.size() > 0) {
                sectionTitle(doc, "Próximos Pasos", fH11B);
                PdfPTable pasosTable = new PdfPTable(1);
                pasosTable.setWidthPercentage(100);
                pasosTable.setSpacingAfter(14);
                PdfPCell pasosCell = new PdfPCell();
                pasosCell.setPadding(12);
                pasosCell.setBorderColor(BORDER);
                pasosCell.setBorderWidth(0.5f);
                Paragraph pasosParagraph = new Paragraph();
                int num = 1;
                for (JsonNode paso : pasos) {
                    pasosParagraph.add(new Chunk(num++ + ". " + paso.asText() + "\n", fH9));
                }
                pasosCell.addElement(pasosParagraph);
                pasosTable.addCell(pasosCell);
                doc.add(pasosTable);
            }

        } finally {
            doc.close();
        }

        return baos.toByteArray();
    }

    // ── Seguimiento de proyectos ──────────────────────────────────────────────

    @Transactional
    public BrujulaProyecto iniciarSeguimiento(Long analisisId, String usuarioId) {
        BrujulaAnalisis analisis = analisisRepo.findById(analisisId)
                .orElseThrow(() -> new RuntimeException("[Brújula] Análisis no encontrado: " + analisisId));

        String nombreProyecto = "Proyecto #" + analisisId;
        if (analisis.getOutputData() != null) {
            try {
                JsonNode json = objectMapper.readTree(analisis.getOutputData());
                String resumen = json.path("comprension").path("resumen").asText("");
                if (!resumen.isBlank() && resumen.length() > 10) {
                    nombreProyecto = resumen.length() > 60 ? resumen.substring(0, 57) + "..." : resumen;
                }
            } catch (Exception e) {
                log.warn("[Brújula] no se pudo extraer nombre del proyecto desde análisis: {}", e.getMessage());
            }
        }

        String hitosJson = null;
        if (analisis.getOutputData() != null) {
            try {
                JsonNode json = objectMapper.readTree(analisis.getOutputData());
                JsonNode hitos = json.path("hitos");
                if (!hitos.isMissingNode()) hitosJson = objectMapper.writeValueAsString(hitos);
            } catch (Exception ignored) {}
        }

        BrujulaProyecto proyecto = new BrujulaProyecto();
        proyecto.setAnalisisId(analisisId);
        proyecto.setUsuarioId(usuarioId);
        proyecto.setNombreProyecto(nombreProyecto);
        proyecto.setEstado("activo");
        proyecto.setProximoCheckin(LocalDateTime.now().plusDays(30));
        proyecto.setHitos(hitosJson);
        proyecto.setCreatedAt(LocalDateTime.now());
        proyecto = proyectoRepo.save(proyecto);

        log.info("[Brújula] seguimiento iniciado proyectoId={} usuario='{}' nombre='{}'",
                proyecto.getId(), maskId(usuarioId), nombreProyecto);
        return proyecto;
    }

    @Transactional
    public void procesarCheckin(BrujulaProyecto proyecto) {
        log.info("[Brújula] procesando check-in proyectoId={} usuario='{}'",
                proyecto.getId(), maskId(proyecto.getUsuarioId()));
        try {
            String mensaje = generarMensajeCheckin(proyecto);
            twilioService.sendWhatsApp(proyecto.getUsuarioId(), mensaje);

            BrujulaCheckin checkin = new BrujulaCheckin();
            checkin.setProyectoId(proyecto.getId());
            checkin.setMensajeAgente(mensaje);
            checkin.setCreatedAt(LocalDateTime.now());
            checkinRepo.save(checkin);

            proyecto.setUltimoCheckin(LocalDateTime.now());
            proyecto.setProximoCheckin(LocalDateTime.now().plusDays(30));
            proyectoRepo.save(proyecto);

            log.info("[Brújula] check-in enviado proyectoId={}", proyecto.getId());
        } catch (Exception e) {
            log.error("[Brújula] error en check-in proyectoId={}: {}", proyecto.getId(), e.getMessage(), e);
        }
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────

    public void procesarMensajeWhatsApp(String usuarioId, String mensaje) {
        log.info("[Brújula] mensaje WhatsApp de usuario='{}'", maskId(usuarioId));
        try {
            List<BrujulaAnalisis> historial = analisisRepo.findByUsuarioIdOrderByCreatedAtDesc(usuarioId);

            String contexto = buildContextoWhatsApp(historial, mensaje);
            String respuesta = generarRespuestaWhatsApp(contexto, mensaje);
            twilioService.sendWhatsApp(usuarioId, respuesta);

            // Si el usuario quiere activar seguimiento sobre el último análisis completado
            if (mensajePideActivarSeguimiento(mensaje) && !historial.isEmpty()) {
                BrujulaAnalisis ultimo = historial.stream()
                        .filter(a -> "completado".equals(a.getEstado()))
                        .findFirst()
                        .orElse(null);
                if (ultimo != null && !tieneProyectoActivo(usuarioId)) {
                    BrujulaProyecto proyecto = iniciarSeguimiento(ultimo.getId(), usuarioId);
                    twilioService.sendWhatsApp(usuarioId,
                            "🧭 Activé el seguimiento de tu proyecto. Te enviaré un check-in en 30 días para ver cómo vas.");
                    log.info("[Brújula] seguimiento activado vía WhatsApp proyectoId={}", proyecto.getId());
                }
            }
        } catch (Exception e) {
            log.error("[Brújula] error procesando WhatsApp usuario='{}': {}", maskId(usuarioId), e.getMessage(), e);
            twilioService.sendWhatsApp(usuarioId,
                    "Tuve un problema procesando tu consulta. Por favor intentá de nuevo en un momento.");
        }
    }

    // ── Privados: lógica de IA ────────────────────────────────────────────────

    private String generarMensajeCheckin(BrujulaProyecto proyecto) {
        int totalCheckins = (int) checkinRepo.countByProyectoId(proyecto.getId());
        String historialCheckins = buildHistorialCheckins(proyecto.getId());

        String prompt = String.format("""
                Eres Brújula, consultor de negocios. Debes generar un mensaje de check-in mensual
                para un emprendedor que está ejecutando su proyecto.

                PROYECTO: %s
                CHECK-IN NÚMERO: %d
                PLAN DE HITOS: %s
                HISTORIAL DE CHECK-INS PREVIOS: %s

                Genera un mensaje de WhatsApp motivador y concreto (máximo 3 párrafos):
                1. Saluda y menciona el número de check-in
                2. Pregunta por una métrica o hito específico del plan
                3. Ofrece un consejo o recordatorio concreto
                Habla en segunda persona, tono cercano, en español de Chile. Sin markdown.
                """,
                proyecto.getNombreProyecto(),
                totalCheckins + 1,
                proyecto.getHitos() != null ? proyecto.getHitos() : "(sin hitos registrados)",
                historialCheckins.isBlank() ? "Este es el primer check-in." : historialCheckins
        );

        return client.messages().create(
                MessageCreateParams.builder()
                        .model(Model.CLAUDE_HAIKU_4_5_20251001)
                        .maxTokens(400L)
                        .system("Eres Brújula de Kin. Genera mensajes de seguimiento para emprendedores. Máximo 3 párrafos cortos.")
                        .addUserMessage(prompt)
                        .build()
        ).content().stream()
                .filter(b -> b.isText())
                .map(b -> b.asText().text())
                .collect(Collectors.joining())
                .trim();
    }

    private String generarRespuestaWhatsApp(String contexto, String mensaje) {
        return client.messages().create(
                MessageCreateParams.builder()
                        .model(Model.CLAUDE_HAIKU_4_5_20251001)
                        .maxTokens(400L)
                        .system("""
                                Eres Brújula de Kin, consultor de negocios por WhatsApp.
                                Responde consultas de emprendedores sobre sus ideas y proyectos.
                                Máximo 3 párrafos. Tono cercano, en español de Chile. Sin markdown.
                                Si el usuario pregunta por su análisis, resume los puntos clave.
                                """)
                        .addUserMessage(contexto + "\n\nMensaje del usuario: " + mensaje)
                        .build()
        ).content().stream()
                .filter(b -> b.isText())
                .map(b -> b.asText().text())
                .collect(Collectors.joining())
                .trim();
    }

    // ── Privados: helpers ─────────────────────────────────────────────────────

    private String buildUserMessage(BrujulaRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("TIPO DE EVALUACIÓN: ").append(req.getTipoEvaluacion()).append("\n\n");

        if (req.getDatosnegocio() != null && !req.getDatosnegocio().isEmpty()) {
            sb.append("DESCRIPCIÓN DEL NEGOCIO:\n");
            req.getDatosnegocio().forEach((k, v) ->
                    sb.append("  ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("UBICACIÓN: ").append(req.getRegion()).append(", ").append(req.getComuna()).append("\n");
        sb.append("TIPO DE OPERACIÓN: ").append(req.getTipoOperacion()).append("\n");

        if (req.getMercado() != null && !req.getMercado().isEmpty()) {
            sb.append("MERCADO OBJETIVO: ").append(String.join(", ", req.getMercado())).append("\n");
        }

        sb.append("\nRECURSOS DEL EMPRENDEDOR:\n");
        sb.append("  Capital disponible: ").append(fmtClp(req.getCapitalDisponible() != null ? req.getCapitalDisponible() : 0)).append("\n");
        sb.append("  Tiempo disponible: ").append(req.getTiempoDisponible()).append("\n");
        sb.append("  Experiencia previa: ").append(req.getExperiencia()).append("\n");
        sb.append("  Socios: ").append(req.getSocios()).append("\n");

        sb.append("\nMETAS Y HORIZONTE:\n");
        sb.append("  Objetivo declarado: ").append(req.getObjetivo()).append("\n");
        sb.append("  Meta mínima mensual: ").append(fmtClp(req.getMetaMinima() != null ? req.getMetaMinima() : 0)).append("\n");
        sb.append("  Meta realista mensual: ").append(fmtClp(req.getMetaRealista() != null ? req.getMetaRealista() : 0)).append("\n");
        sb.append("  Meta ambiciosa mensual: ").append(fmtClp(req.getMetaAmbiciosa() != null ? req.getMetaAmbiciosa() : 0)).append("\n");
        sb.append("  Horizonte de tiempo: ").append(req.getHorizonte()).append("\n");

        if (req.getSacrificios() != null && !req.getSacrificios().isEmpty()) {
            sb.append("  No estoy dispuesto a: ").append(String.join(", ", req.getSacrificios())).append("\n");
        }

        sb.append("  Tolerancia al riesgo: ").append(req.getToleranciaRiesgo()).append("\n");

        return sb.toString();
    }

    private String buildContextoWhatsApp(List<BrujulaAnalisis> historial, String mensaje) {
        if (historial.isEmpty()) return "El usuario no tiene análisis previos.";
        BrujulaAnalisis ultimo = historial.get(0);
        StringBuilder sb = new StringBuilder("CONTEXTO DEL USUARIO:\n");
        sb.append("Tiene ").append(historial.size()).append(" análisis previo(s).\n");
        sb.append("Último análisis: #").append(ultimo.getId())
                .append(" (").append(ultimo.getTipoEvaluacion()).append(")")
                .append(" — veredicto: ").append(ultimo.getVeredicto()).append("\n");
        if (ultimo.getOutputData() != null) {
            try {
                JsonNode json = objectMapper.readTree(ultimo.getOutputData());
                sb.append("Resumen: ").append(json.path("comprension").path("resumen").asText("")).append("\n");
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private String buildHistorialCheckins(Long proyectoId) {
        List<BrujulaCheckin> checkins = checkinRepo.findByProyectoIdOrderByCreatedAtDesc(proyectoId);
        if (checkins.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (BrujulaCheckin c : checkins.stream().limit(3).toList()) {
            sb.append("- Check-in ").append(c.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append(": ");
            if (c.getRespuestaUsuario() != null) sb.append(c.getRespuestaUsuario(), 0, Math.min(100, c.getRespuestaUsuario().length()));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String extraerVeredicto(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("veredicto").path("tipo").asText("pendiente");
        } catch (Exception e) {
            log.warn("[Brújula] no se pudo extraer veredicto del JSON: {}", e.getMessage());
            return "pendiente";
        }
    }

    private String stripMarkdownFences(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    private String resolverUsuarioId(BrujulaRequest request) {
        if (request.getTelefono() != null && !request.getTelefono().isBlank()) {
            return request.getTelefono().replaceAll("[^0-9]", "");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return request.getEmail().toLowerCase();
        }
        return "anon-" + System.currentTimeMillis();
    }

    private boolean mensajePideActivarSeguimiento(String mensaje) {
        String m = mensaje.toLowerCase();
        return m.contains("activar seguimiento") || m.contains("seguimiento") ||
               m.contains("hacer seguimiento") || m.contains("seguir este proyecto");
    }

    private boolean tieneProyectoActivo(String usuarioId) {
        return proyectoRepo.countByUsuarioIdAndEstado(usuarioId, "activo") > 0;
    }

    private String mesActual() {
        return java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String maskId(String id) {
        if (id == null || id.length() <= 4) return "***";
        return "*".repeat(id.length() - 4) + id.substring(id.length() - 4);
    }

    private String joinArray(JsonNode array, String separator) {
        if (!array.isArray() || array.size() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(array.get(i).asText());
        }
        return sb.toString();
    }

    // ── PDF helpers ───────────────────────────────────────────────────────────

    private static void sectionTitle(Document doc, String title, Font font) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        t.setSpacingAfter(6);
        PdfPCell c = new PdfPCell(new Phrase(title, font));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(ACCENT);
        c.setBorderWidthBottom(1.5f);
        c.setPaddingBottom(5);
        c.setPaddingLeft(0);
        c.setBackgroundColor(Color.WHITE);
        t.addCell(c);
        doc.add(t);
    }

    private static void addTextBlock(Document doc, String text, Font font) throws DocumentException {
        if (text == null || text.isBlank()) return;
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(6f);
        doc.add(p);
    }

    private static void addScoreCard(PdfPTable table, String label, int score) {
        Color scoreColor = score >= 7 ? C_GREEN : score >= 5 ? C_ORANGE : C_RED;
        Font labelFont = fnt(FontFactory.HELVETICA,      8, TXT_MID);
        Font scoreFont = fnt(FontFactory.HELVETICA_BOLD, 16, scoreColor);

        PdfPCell c = new PdfPCell();
        c.setPadding(12);
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        c.setBorderWidthTop(3f);
        c.setBorderColorTop(scoreColor);
        c.setBackgroundColor(Color.WHITE);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", labelFont));
        p.add(new Chunk(score + "/10", scoreFont));
        c.addElement(p);
        table.addCell(c);
    }

    private static void tblHeader(PdfPTable table, String[] cols) {
        Font f = fnt(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String col : cols) {
            PdfPCell c = new PdfPCell(new Phrase(col, f));
            c.setBackgroundColor(DARK_BG);
            c.setPadding(7);
            c.setBorder(Rectangle.NO_BORDER);
            table.addCell(c);
        }
    }

    private static void tblRow(PdfPTable table, String[] values, Color bg, Font font) {
        for (String value : values) {
            PdfPCell c = new PdfPCell(new Phrase(value != null ? value : "", font));
            c.setBackgroundColor(bg);
            c.setPadding(6);
            c.setBorderColor(BORDER);
            c.setBorderWidth(0.5f);
            table.addCell(c);
        }
    }

    private static Color veredictoColor(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "viable"             -> C_GREEN;
            case "viable_ajustes"     -> new Color(100, 180, 80);
            case "requiere_recursos"  -> C_ORANGE;
            case "poco_viable"        -> new Color(200, 120, 40);
            case "no_recomendable"    -> C_RED;
            default                   -> TXT_MID;
        };
    }

    private static String fmtClp(long amount) {
        if (amount == 0) return "$0";
        String s = String.valueOf(Math.abs(amount));
        StringBuilder r = new StringBuilder();
        int cnt = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (cnt > 0 && cnt % 3 == 0) r.insert(0, '.');
            r.insert(0, s.charAt(i));
            cnt++;
        }
        return (amount < 0 ? "-$" : "$") + r;
    }

    private static Font fnt(String family, float size, Color color) {
        return FontFactory.getFont(family, "Cp1252", false, size, Font.NORMAL, color);
    }

    // ── Footer del PDF ────────────────────────────────────────────────────────

    private static class BrujulaFooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Font f = fnt(FontFactory.HELVETICA, 8, TXT_LITE);
                Phrase left  = new Phrase("Generado por Kin · Brújula — kinagentes.com", f);
                Phrase right = new Phrase("pag. " + writer.getPageNumber(), f);
                ColumnText.showTextAligned(writer.getDirectContent(),
                        Element.ALIGN_LEFT, left, document.leftMargin(), 26, 0);
                ColumnText.showTextAligned(writer.getDirectContent(),
                        Element.ALIGN_RIGHT, right,
                        document.getPageSize().getWidth() - document.rightMargin(), 26, 0);
            } catch (Exception ignored) {}
        }
    }
}
