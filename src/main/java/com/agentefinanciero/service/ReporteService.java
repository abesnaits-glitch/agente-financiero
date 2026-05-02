package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    // ── PDF light palette (tables / text sections) ────────────────────────────
    private static final Color DARK_BG  = new Color(30, 32, 41);
    private static final Color ACCENT   = new Color(127, 119, 221);
    private static final Color C_GREEN  = new Color(72, 185, 142);
    private static final Color C_RED    = new Color(215, 80, 80);
    private static final Color TXT_DARK = new Color(30, 32, 41);
    private static final Color TXT_MID  = new Color(90, 90, 110);
    private static final Color TXT_LITE = new Color(160, 160, 180);
    private static final Color ROW_ALT  = new Color(245, 245, 252);
    private static final Color BORDER   = new Color(218, 218, 232);
    private static final Color HDR_ALT  = new Color(240, 240, 248);

    // ── Chart light palette (white background) ───────────────────────────────
    private static final Color CHART_BG     = new Color(255, 255, 255);  // #ffffff
    private static final Color CHART_CARD   = new Color(236, 236, 240);  // track/cell background
    private static final Color CHART_TEXT   = new Color(26,  29,  39);   // #1a1d27
    private static final Color CHART_MUTED  = new Color(130, 130, 145);  // medium gray
    private static final Color CHART_GRID   = new Color(224, 224, 224);  // #e0e0e0
    private static final Color CHART_ACCENT = new Color(0,  229, 160);   // #00e5a0
    private static final Color CHART_YELLOW = new Color(180, 120,   0);  // dark gold, legible on white

    private static final Color[] CHART_COLORS = {
        new Color(0,   229, 160),  // #00e5a0 green
        new Color(108,  99, 255),  // #6c63ff purple
        new Color(239, 159,  39),  // orange
        new Color(216,  90,  48),  // red-orange
        new Color( 74, 144, 217),  // blue
        new Color(160,  90, 180),  // violet
        new Color( 60, 180, 180),  // teal
        new Color(200, 130,  60),  // brown
    };

    @Value("${app.reports-dir:/tmp/faro-reports}")
    private String reportsDirPath;

    private final GastoService gastoService;
    private final UsuarioPerfilRepository perfilRepository;
    private final ClaudeService claudeService;

    public ReporteService(GastoService gastoService,
                          UsuarioPerfilRepository perfilRepository,
                          ClaudeService claudeService) {
        this.gastoService    = gastoService;
        this.perfilRepository = perfilRepository;
        this.claudeService   = claudeService;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public String generarReporte(String usuarioId) {
        return generarReporte(usuarioId, YearMonth.now());
    }

    public String generarReporte(String usuarioId, YearMonth mes) {
        log.info("[Reporte] generando PDF para usuario '{}' mes={}", usuarioId, mes);
        GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(usuarioId, mes);
        UsuarioPerfil perfil = perfilRepository.findById(usuarioId).orElse(null);

        try {
            Path dir = Paths.get(reportsDirPath);
            Files.createDirectories(dir);

            String prefix = "reporte_" + usuarioId + "_";
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }

            String filename = String.format("reporte_%s_%02d_%d.pdf",
                    usuarioId, mes.getMonthValue(), mes.getYear());
            Path file = dir.resolve(filename);

            buildPdf(file, resumen, perfil, mes);

            log.info("[Reporte] PDF guardado: {}", file);
            String base = System.getenv("BASE_URL") != null
                    ? System.getenv("BASE_URL") : "http://localhost:8080";
            return base + "/reports/" + filename;

        } catch (Exception e) {
            throw new RuntimeException("Error generando reporte PDF", e);
        }
    }

    // ── PDF assembly ──────────────────────────────────────────────────────────

    private void buildPdf(Path file, GastoService.ResumenFinanciero resumen,
                          UsuarioPerfil perfil, YearMonth mes) throws Exception {

        LocalDate now     = LocalDate.now();
        String mesNombre  = capitalize(mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")));
        String periodo    = mesNombre + " " + mes.getYear();
        String nombre     = perfil != null && perfil.getNombre() != null ? perfil.getNombre() : null;
        BigDecimal presup = perfil != null ? perfil.getPresupuestoMensual() : null;

        // Pre-compute gastosCat once — reused by charts and detail table
        Map<String, BigDecimal> gastosCat = resumen.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria() != null ? capitalize(g.getCategoria()) : "Sin categoria",
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        LocalDate endDate = mes.equals(YearMonth.now()) ? now : mes.atEndOfMonth();

        Document doc = new Document(PageSize.A4, 48, 48, 30, 50);
        FileOutputStream fos = new FileOutputStream(file.toFile());
        PdfWriter writer = PdfWriter.getInstance(doc, fos);
        writer.setPageEvent(new FooterEvent(periodo));
        doc.open();

        try {
            // Fonts
            Font fH18B = fnt(FontFactory.HELVETICA_BOLD,  18, Color.WHITE);
            Font fH13  = fnt(FontFactory.HELVETICA,       13, TXT_LITE);
            Font fH11B = fnt(FontFactory.HELVETICA_BOLD,  11, TXT_DARK);
            Font fH10  = fnt(FontFactory.HELVETICA,       10, TXT_LITE);
            Font fH9   = fnt(FontFactory.HELVETICA,        9, TXT_MID);
            Font fH9B  = fnt(FontFactory.HELVETICA_BOLD,   9, TXT_DARK);
            Font fH8   = fnt(FontFactory.HELVETICA,        8, TXT_LITE);

            // ── Header ─────────────────────────────────────────────────────────
            PdfPTable hdr = new PdfPTable(new float[]{3, 1});
            hdr.setWidthPercentage(100);
            hdr.setSpacingAfter(14);

            PdfPCell hdrLeft = new PdfPCell();
            hdrLeft.setBackgroundColor(DARK_BG);
            hdrLeft.setPadding(16);
            hdrLeft.setBorder(Rectangle.NO_BORDER);
            Paragraph hdrTitle = new Paragraph();
            hdrTitle.add(new Chunk("FARO  ", fH18B));
            hdrTitle.add(new Chunk("Reporte Financiero", fH13));
            hdrLeft.addElement(hdrTitle);
            String subline = nombre != null ? periodo + "  ·  " + nombre : periodo;
            hdrLeft.addElement(new Phrase(subline, fH10));
            hdr.addCell(hdrLeft);

            PdfPCell hdrRight = new PdfPCell(
                new Phrase(now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fH10));
            hdrRight.setBackgroundColor(DARK_BG);
            hdrRight.setPadding(16);
            hdrRight.setBorder(Rectangle.NO_BORDER);
            hdrRight.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hdrRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
            hdr.addCell(hdrRight);
            doc.add(hdr);

            // ── Charts ──────────────────────────────────────────────────────────
            float usableW = doc.getPageSize().getWidth() - doc.leftMargin() - doc.rightMargin();
            int halfW = (int)(usableW / 2f) - 3;
            int chartH = 170;

            // Row 1: donut (left) + horizontal bars (right)
            {
                Image donutImg = buildDonutChart(writer, gastosCat, resumen.totalGastado(), halfW, chartH);
                Image barsImg  = buildHBarsChart(writer, gastosCat, resumen.totalGastado(), presup, halfW, chartH);

                PdfPTable row = new PdfPTable(2);
                row.setWidthPercentage(100);
                row.setSpacingAfter(5);

                PdfPCell c1 = new PdfPCell(donutImg, true);
                c1.setBorder(Rectangle.NO_BORDER);
                c1.setPadding(0);
                c1.setPaddingRight(3);

                PdfPCell c2 = new PdfPCell(barsImg, true);
                c2.setBorder(Rectangle.NO_BORDER);
                c2.setPadding(0);
                c2.setPaddingLeft(3);

                row.addCell(c1);
                row.addCell(c2);
                doc.add(row);
            }

            // Row 2: line chart (full width)
            {
                Image lineImg = buildLineChart(writer, resumen.movimientos(), endDate, (int) usableW, 118);

                PdfPTable row = new PdfPTable(1);
                row.setWidthPercentage(100);
                row.setSpacingAfter(18);

                PdfPCell cell = new PdfPCell(lineImg, true);
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setPadding(0);

                row.addCell(cell);
                doc.add(row);
            }

            // ── Summary cards ───────────────────────────────────────────────────
            sectionTitle(doc, "Resumen del Mes", fH11B);
            BigDecimal balance = resumen.totalIngresado().subtract(resumen.totalGastado());
            boolean surplus    = balance.compareTo(BigDecimal.ZERO) >= 0;

            PdfPTable cards = new PdfPTable(presup != null ? 4 : 3);
            cards.setWidthPercentage(100);
            cards.setSpacingAfter(18);
            addCard(cards, "INGRESOS DEL MES", fmtMoney(resumen.totalIngresado()), null, C_GREEN, fH8);
            addCard(cards, "GASTOS DEL MES",   fmtMoney(resumen.totalGastado()),   null, C_RED,   fH8);
            addCard(cards, "BALANCE", (surplus ? "+" : "") + fmtMoney(balance), null,
                    surplus ? C_GREEN : C_RED, fH8);
            if (presup != null) {
                BigDecimal disp = presup.subtract(resumen.totalGastado());
                boolean over = disp.compareTo(BigDecimal.ZERO) < 0;
                addCard(cards, "DISPONIBLE",
                        (over ? "-" : "") + fmtMoney(disp.abs()),
                        "de " + fmtMoney(presup),
                        over ? C_RED : C_GREEN, fH8);
            }
            doc.add(cards);

            // ── Category detail table ───────────────────────────────────────────
            if (!gastosCat.isEmpty()) {
                sectionTitle(doc, "Gastos por Categoria", fH11B);
                PdfPTable catTbl = new PdfPTable(new float[]{3f, 2f, 1.5f});
                catTbl.setWidthPercentage(100);
                catTbl.setSpacingAfter(18);
                tblHeader(catTbl, new String[]{"Categoria", "Monto", "% del gasto"});

                boolean alt = false;
                for (Map.Entry<String, BigDecimal> e : gastosCat.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).toList()) {
                    double pct = resumen.totalGastado().compareTo(BigDecimal.ZERO) == 0 ? 0
                            : e.getValue().divide(resumen.totalGastado(), 4, RoundingMode.HALF_UP)
                              .doubleValue() * 100;
                    Color bg = alt ? ROW_ALT : Color.WHITE;
                    tblRow(catTbl,
                            new String[]{e.getKey(), fmtMoney(e.getValue()),
                                    String.format(Locale.US, "%.1f%%", pct)},
                            bg, fH9, new boolean[]{false, false, true});
                    alt = !alt;
                }
                tblTotalRow(catTbl, "TOTAL GASTOS", fmtMoney(resumen.totalGastado()), "100%", fH9B);
                doc.add(catTbl);
            }

            // ── Transactions table ──────────────────────────────────────────────
            if (!resumen.movimientos().isEmpty()) {
                sectionTitle(doc, "Detalle de Movimientos", fH11B);
                PdfPTable txTbl = new PdfPTable(new float[]{1.3f, 3f, 2f, 1.5f});
                txTbl.setWidthPercentage(100);
                txTbl.setSpacingAfter(16);
                tblHeader(txTbl, new String[]{"Fecha", "Descripcion", "Categoria", "Monto"});

                boolean alt = false;
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");
                for (Gasto g : resumen.movimientos()) {
                    boolean isGasto = "gasto".equals(g.getTipo());
                    Font amtFont = fnt(FontFactory.HELVETICA_BOLD, 9, isGasto ? C_RED : C_GREEN);
                    String montoStr = (isGasto ? "-" : "+") + fmtMoney(g.getMonto());
                    String desc = g.getDescripcion() != null && !g.getDescripcion().isBlank()
                            ? capitalize(g.getDescripcion())
                            : (g.getCategoria() != null ? capitalize(g.getCategoria()) : "-");
                    String cat = g.getCategoria() != null ? capitalize(g.getCategoria()) : "-";
                    Color bg = alt ? ROW_ALT : Color.WHITE;
                    txTbl.addCell(tblCell(g.getFecha().format(dtf), fH9,    bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(desc,                     fH9,    bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(cat,                      fH9,    bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(montoStr,                 amtFont, bg, Element.ALIGN_RIGHT));
                    alt = !alt;
                }
                doc.add(txTbl);
            }

            // ── Analysis & advice (Claude) ──────────────────────────────────────
            String prompt = buildAnalysisPrompt(resumen, gastosCat, presup, periodo);
            log.info("[Reporte] generando analisis con Claude...");
            String consejos = claudeService.generarConsejosFinancieros(prompt);
            if (consejos != null && !consejos.isBlank()) {
                addAnalysisSection(doc, consejos, fH9, fH11B);
            }

        } finally {
            doc.close();
            fos.close();
        }
    }

    // ── Chart: donut ──────────────────────────────────────────────────────────
    // Draws an annular (donut) chart with sectors for each spending category.
    // Sectors are drawn clockwise from 12 o'clock using arc paths on PdfTemplate.

    private static Image buildDonutChart(PdfWriter writer, Map<String, BigDecimal> gastosCat,
            BigDecimal totalGastado, int w, int h) throws Exception {

        PdfTemplate tpl = writer.getDirectContent().createTemplate(w, h);
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);

        tpl.setColorFill(CHART_BG);
        tpl.rectangle(0, 0, w, h);
        tpl.fill();

        tpl.beginText();
        tpl.setFontAndSize(bfBold, 8.5f);
        tpl.setColorFill(CHART_TEXT);
        tpl.showTextAligned(PdfContentByte.ALIGN_LEFT, "Por Categoria", 7, h - 13, 0);
        tpl.endText();

        List<Map.Entry<String, BigDecimal>> entries = gastosCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(6).toList();

        if (totalGastado.compareTo(BigDecimal.ZERO) == 0 || entries.isEmpty()) {
            tpl.beginText();
            tpl.setFontAndSize(bf, 8);
            tpl.setColorFill(CHART_MUTED);
            tpl.showTextAligned(PdfContentByte.ALIGN_CENTER, "Sin gastos", w / 2f, h / 2f, 0);
            tpl.endText();
            return Image.getInstance(tpl);
        }

        // Donut geometry occupies the left square portion of the template
        float cx     = h / 2f;
        float cy     = h / 2f;
        float rOuter = h * 0.36f;
        float rInner = rOuter * 0.55f;

        // Draw sectors clockwise from 90° (top). arc() extent < 0 = clockwise in PDF coords.
        double angle = 90.0;
        tpl.setLineWidth(0.8f);
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> e = entries.get(i);
            double pct   = e.getValue().divide(totalGastado, 8, RoundingMode.HALF_UP).doubleValue();
            double sweep = pct * 360.0;
            if (sweep < 1.0) { angle -= sweep; continue; }

            double endAngle = angle - sweep;
            double startRad = Math.toRadians(angle);
            double endRad   = Math.toRadians(endAngle);
            Color color     = CHART_COLORS[i % CHART_COLORS.length];

            tpl.setColorFill(color);
            tpl.setColorStroke(CHART_BG);

            // Outer arc → line to inner arc end → inner arc back → close
            tpl.moveTo(cx + rOuter * (float) Math.cos(startRad),
                       cy + rOuter * (float) Math.sin(startRad));
            tpl.arc(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter,
                    (float) angle, (float) -sweep);
            tpl.lineTo(cx + rInner * (float) Math.cos(endRad),
                       cy + rInner * (float) Math.sin(endRad));
            tpl.arc(cx - rInner, cy - rInner, cx + rInner, cy + rInner,
                    (float) endAngle, (float) sweep);
            tpl.closePath();
            tpl.fillStroke();

            angle = endAngle;
        }

        // Donut hole
        tpl.setColorFill(CHART_BG);
        tpl.setColorStroke(CHART_BG);
        tpl.circle(cx, cy, rInner - 0.5f);
        tpl.fill();

        // Center label
        tpl.beginText();
        tpl.setFontAndSize(bfBold, 8f);
        tpl.setColorFill(CHART_TEXT);
        tpl.showTextAligned(PdfContentByte.ALIGN_CENTER, fmtMoney(totalGastado), cx, cy + 2f, 0);
        tpl.endText();
        tpl.beginText();
        tpl.setFontAndSize(bf, 6.5f);
        tpl.setColorFill(CHART_MUTED);
        tpl.showTextAligned(PdfContentByte.ALIGN_CENTER, "total", cx, cy - 9f, 0);
        tpl.endText();

        // Legend on the right side of the template
        float legX  = h + 6f;
        float rowSp = Math.min(22f, (h - 30f) / entries.size());
        float legY  = h - 22f;

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, BigDecimal> e = entries.get(i);
            double pct  = e.getValue().divide(totalGastado, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            Color color = CHART_COLORS[i % CHART_COLORS.length];

            tpl.setColorFill(color);
            tpl.roundRectangle(legX, legY, 7, 7, 1.5f);
            tpl.fill();

            String name = e.getKey();
            if (name.length() > 11) name = name.substring(0, 10) + "…";
            tpl.beginText();
            tpl.setFontAndSize(bf, 7.5f);
            tpl.setColorFill(CHART_TEXT);
            tpl.showTextAligned(PdfContentByte.ALIGN_LEFT, name, legX + 10f, legY, 0);
            tpl.endText();

            tpl.beginText();
            tpl.setFontAndSize(bf, 7.5f);
            tpl.setColorFill(CHART_MUTED);
            tpl.showTextAligned(PdfContentByte.ALIGN_RIGHT,
                    String.format(Locale.US, "%.0f%%", pct), w - 4f, legY, 0);
            tpl.endText();

            legY -= rowSp;
        }

        return Image.getInstance(tpl);
    }

    // ── Chart: horizontal bars ────────────────────────────────────────────────
    // Filled bars proportional to each category's share of total spending.
    // Optionally shows budget usage summary at the bottom.

    private static Image buildHBarsChart(PdfWriter writer, Map<String, BigDecimal> gastosCat,
            BigDecimal totalGastado, BigDecimal presupuesto, int w, int h) throws Exception {

        PdfTemplate tpl = writer.getDirectContent().createTemplate(w, h);
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);

        tpl.setColorFill(CHART_BG);
        tpl.rectangle(0, 0, w, h);
        tpl.fill();

        tpl.beginText();
        tpl.setFontAndSize(bfBold, 8.5f);
        tpl.setColorFill(CHART_TEXT);
        tpl.showTextAligned(PdfContentByte.ALIGN_LEFT, "Distribucion por Categoria", 7, h - 13, 0);
        tpl.endText();

        List<Map.Entry<String, BigDecimal>> sorted = gastosCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5).toList();

        if (sorted.isEmpty() || totalGastado.compareTo(BigDecimal.ZERO) == 0) {
            tpl.beginText();
            tpl.setFontAndSize(bf, 8);
            tpl.setColorFill(CHART_MUTED);
            tpl.showTextAligned(PdfContentByte.ALIGN_CENTER, "Sin datos", w / 2f, h / 2f, 0);
            tpl.endText();
            return Image.getInstance(tpl);
        }

        float labelW  = 54f;
        float amtW    = 38f;
        float barX    = labelW + 6f;
        float barMaxW = w - labelW - amtW - 14f;
        float bottomY = (presupuesto != null && presupuesto.compareTo(BigDecimal.ZERO) > 0) ? 16f : 4f;
        float rowH    = (h - 26f - bottomY) / sorted.size();
        float y       = h - 24f;

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, BigDecimal> e = sorted.get(i);
            float pct   = e.getValue().divide(totalGastado, 4, RoundingMode.HALF_UP).floatValue();
            Color color = CHART_COLORS[i % CHART_COLORS.length];

            float barY    = y - rowH * 0.62f;
            float barH    = rowH * 0.46f;
            float fillW   = Math.max(2f, pct * barMaxW);

            // Track
            tpl.setColorFill(CHART_CARD);
            tpl.roundRectangle(barX, barY, barMaxW, barH, 2f);
            tpl.fill();

            // Fill
            tpl.setColorFill(color);
            tpl.roundRectangle(barX, barY, fillW, barH, 2f);
            tpl.fill();

            // Category label
            String label = e.getKey();
            if (label.length() > 9) label = label.substring(0, 8) + "…";
            tpl.beginText();
            tpl.setFontAndSize(bf, 7.5f);
            tpl.setColorFill(CHART_TEXT);
            tpl.showTextAligned(PdfContentByte.ALIGN_RIGHT, label, barX - 3f, barY + barH * 0.2f, 0);
            tpl.endText();

            // Amount
            tpl.beginText();
            tpl.setFontAndSize(bf, 7f);
            tpl.setColorFill(color);
            tpl.showTextAligned(PdfContentByte.ALIGN_LEFT,
                    fmtMoney(e.getValue()), barX + barMaxW + 4f, barY + barH * 0.2f, 0);
            tpl.endText();

            // Percentage (inside bar if wide enough, outside otherwise)
            String pctStr = String.format(Locale.US, "%.0f%%", pct * 100);
            if (fillW > 24f) {
                tpl.beginText();
                tpl.setFontAndSize(bf, 6.5f);
                tpl.setColorFill(CHART_BG);
                tpl.showTextAligned(PdfContentByte.ALIGN_RIGHT,
                        pctStr, barX + fillW - 3f, barY + barH * 0.2f, 0);
                tpl.endText();
            }

            y -= rowH;
        }

        // Budget summary line
        if (presupuesto != null && presupuesto.compareTo(BigDecimal.ZERO) > 0) {
            double usedPct = totalGastado.divide(presupuesto, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            boolean over = usedPct > 100;
            tpl.beginText();
            tpl.setFontAndSize(bfBold, 7f);
            tpl.setColorFill(over ? CHART_COLORS[3] : CHART_ACCENT);
            tpl.showTextAligned(PdfContentByte.ALIGN_LEFT,
                    String.format(Locale.US, "Presupuesto: %s  (%.0f%% usado)",
                            fmtMoney(totalGastado), usedPct),
                    7, 5, 0);
            tpl.endText();
        }

        return Image.getInstance(tpl);
    }

    // ── Chart: line (daily spending) ──────────────────────────────────────────
    // Area + line chart of spending over the last 14 days relative to endDate.
    // Peak day is highlighted in yellow. Filled area uses PdfGState transparency.

    private static Image buildLineChart(PdfWriter writer, List<Gasto> movimientos,
            LocalDate endDate, int w, int h) throws Exception {

        PdfTemplate tpl = writer.getDirectContent().createTemplate(w, h);
        BaseFont bf     = BaseFont.createFont(BaseFont.HELVETICA,      BaseFont.CP1252, false);
        BaseFont bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, false);

        tpl.setColorFill(CHART_BG);
        tpl.rectangle(0, 0, w, h);
        tpl.fill();

        tpl.beginText();
        tpl.setFontAndSize(bfBold, 8.5f);
        tpl.setColorFill(CHART_TEXT);
        tpl.showTextAligned(PdfContentByte.ALIGN_LEFT, "Gasto Diario - Ultimos 14 Dias", 7, h - 13, 0);
        tpl.endText();

        // Build daily data
        LocalDate[] days = new LocalDate[14];
        double[]    vals = new double[14];
        for (int i = 0; i < 14; i++) days[i] = endDate.minusDays(13 - i);

        Map<LocalDate, BigDecimal> daily = movimientos.stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(Gasto::getFecha,
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        for (int i = 0; i < 14; i++) {
            BigDecimal v = daily.get(days[i]);
            vals[i] = v != null ? v.doubleValue() : 0;
        }

        double maxVal = 0;
        for (double v : vals) if (v > maxVal) maxVal = v;

        if (maxVal == 0) {
            tpl.beginText();
            tpl.setFontAndSize(bf, 8);
            tpl.setColorFill(CHART_MUTED);
            tpl.showTextAligned(PdfContentByte.ALIGN_CENTER,
                    "Sin gastos en este periodo", w / 2f, h / 2f, 0);
            tpl.endText();
            return Image.getInstance(tpl);
        }

        maxVal = roundUpNice(maxVal);

        // Plot area bounds
        float pX0 = 46f, pX1 = w - 10f;
        float pY0 = 18f, pY1 = h - 24f;
        float pW  = pX1 - pX0;
        float pH  = pY1 - pY0;
        float xStep = pW / 13f;

        // Horizontal grid lines with Y-axis labels
        tpl.setColorStroke(CHART_GRID);
        tpl.setLineWidth(0.4f);
        for (int i = 1; i <= 3; i++) {
            float gy = pY0 + pH * i / 3f;
            tpl.moveTo(pX0, gy);
            tpl.lineTo(pX1, gy);
            tpl.stroke();
            tpl.beginText();
            tpl.setFontAndSize(bf, 6f);
            tpl.setColorFill(CHART_MUTED);
            tpl.showTextAligned(PdfContentByte.ALIGN_RIGHT,
                    fmtMoneyShort(maxVal * i / 3.0), pX0 - 2f, gy - 2.5f, 0);
            tpl.endText();
        }

        // Point positions + peak index
        float[] px = new float[14];
        float[] py = new float[14];
        int maxIdx = 0;
        for (int i = 0; i < 14; i++) {
            px[i] = pX0 + i * xStep;
            py[i] = pY0 + (float)(vals[i] / maxVal) * pH;
            if (vals[i] > vals[maxIdx]) maxIdx = i;
        }

        // Filled area (semi-transparent via PdfGState)
        PdfGState gsAlpha = new PdfGState();
        gsAlpha.setFillOpacity(0.15f);
        tpl.saveState();
        tpl.setGState(gsAlpha);
        tpl.setColorFill(CHART_ACCENT);
        tpl.moveTo(px[0], pY0);
        for (int i = 0; i < 14; i++) tpl.lineTo(px[i], py[i]);
        tpl.lineTo(px[13], pY0);
        tpl.closePath();
        tpl.fill();
        tpl.restoreState();

        // Line
        tpl.setColorStroke(CHART_ACCENT);
        tpl.setLineWidth(1.5f);
        tpl.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        tpl.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        tpl.moveTo(px[0], py[0]);
        for (int i = 1; i < 14; i++) tpl.lineTo(px[i], py[i]);
        tpl.stroke();

        // Dots, X-axis labels, peak annotation
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d/M");
        for (int i = 0; i < 14; i++) {
            boolean isMax = (i == maxIdx) && vals[i] > 0;

            if (vals[i] > 0) {
                tpl.setColorFill(isMax ? CHART_YELLOW : CHART_ACCENT);
                tpl.circle(px[i], py[i], isMax ? 3.5f : 2.2f);
                tpl.fill();
            }

            if (i % 2 == 0 || i == 13) {
                tpl.beginText();
                tpl.setFontAndSize(bf, 6f);
                tpl.setColorFill(CHART_MUTED);
                tpl.showTextAligned(PdfContentByte.ALIGN_CENTER,
                        days[i].format(dtf), px[i], pY0 - 9f, 0);
                tpl.endText();
            }

            if (isMax) {
                tpl.beginText();
                tpl.setFontAndSize(bfBold, 6.5f);
                tpl.setColorFill(CHART_YELLOW);
                tpl.showTextAligned(PdfContentByte.ALIGN_CENTER,
                        fmtMoney(BigDecimal.valueOf(vals[i])), px[i], py[i] + 6f, 0);
                tpl.endText();
            }
        }

        return Image.getInstance(tpl);
    }

    // ── Analysis section ──────────────────────────────────────────────────────

    private static void addAnalysisSection(Document doc, String text,
            Font fH9, Font fH11B) throws DocumentException {
        sectionTitle(doc, "Analisis y Consejos de Faro", fH11B);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setSpacingAfter(16);

        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(245, 244, 255));
        c.setPaddingLeft(14);
        c.setPaddingRight(14);
        c.setPaddingTop(12);
        c.setPaddingBottom(12);
        c.setBorder(Rectangle.LEFT);
        c.setBorderWidthLeft(3.5f);
        c.setBorderColorLeft(ACCENT);

        Font numFont  = fnt(FontFactory.HELVETICA_BOLD, 9, ACCENT);
        Font bodyFont = fnt(FontFactory.HELVETICA,      9, TXT_DARK);
        Font bodyMid  = fnt(FontFactory.HELVETICA,      9, TXT_MID);

        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            Paragraph p;
            if (t.matches("^[123]\\..*")) {
                p = new Paragraph();
                p.add(new Chunk(t.substring(0, 2), numFont));
                p.add(new Chunk(t.substring(2), bodyFont));
            } else {
                p = new Paragraph(t, bodyMid);
            }
            p.setSpacingAfter(6f);
            c.addElement(p);
        }

        wrapper.addCell(c);
        doc.add(wrapper);
    }

    // ── Helpers: prompt building ──────────────────────────────────────────────

    private static String buildAnalysisPrompt(GastoService.ResumenFinanciero resumen,
            Map<String, BigDecimal> gastosCat, BigDecimal presup, String periodo) {

        BigDecimal balance = resumen.totalIngresado().subtract(resumen.totalGastado());
        StringBuilder sb  = new StringBuilder();
        sb.append("Datos financieros de ").append(periodo).append(":\n");
        sb.append("- Ingresos: ").append(fmtMoney(resumen.totalIngresado())).append("\n");
        sb.append("- Gastos: ").append(fmtMoney(resumen.totalGastado())).append("\n");
        sb.append("- Balance: ").append(balance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
          .append(fmtMoney(balance)).append("\n");

        if (presup != null && presup.compareTo(BigDecimal.ZERO) > 0) {
            double uso = resumen.totalGastado().divide(presup, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            sb.append(String.format(Locale.US, "- Presupuesto mensual: %s (%.0f%% usado)\n",
                    fmtMoney(presup), uso));
        }

        if (!gastosCat.isEmpty()) {
            sb.append("\nTop categorias:\n");
            gastosCat.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> {
                        double pct = resumen.totalGastado().compareTo(BigDecimal.ZERO) == 0 ? 0
                                : e.getValue().divide(resumen.totalGastado(), 4, RoundingMode.HALF_UP)
                                  .doubleValue() * 100;
                        sb.append(String.format(Locale.US, "- %s: %s (%.0f%% del gasto)\n",
                                e.getKey(), fmtMoney(e.getValue()), pct));
                    });
        }

        sb.append("- Total movimientos: ").append(resumen.movimientos().size());
        return sb.toString();
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private static void sectionTitle(Document doc, String title, Font font) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(8);
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

    private static void addCard(PdfPTable table, String label, String value, String sub,
                                 Color accent, Font labelFont) {
        Font vf = fnt(FontFactory.HELVETICA_BOLD, 12, accent);
        Font sf = fnt(FontFactory.HELVETICA,       8, TXT_LITE);
        PdfPCell c = new PdfPCell();
        c.setPadding(12);
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        c.setBorderWidthTop(3f);
        c.setBorderColorTop(accent);
        c.setBackgroundColor(Color.WHITE);
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", labelFont));
        p.add(new Chunk(value, vf));
        if (sub != null) p.add(new Chunk("\n" + sub, sf));
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

    private static void tblRow(PdfPTable table, String[] values, Color bg, Font font,
                                boolean[] rightAlign) {
        for (int i = 0; i < values.length; i++) {
            int align = rightAlign != null && rightAlign[i] ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
            table.addCell(tblCell(values[i], font, bg, align));
        }
    }

    private static void tblTotalRow(PdfPTable table, String label, String amount,
                                     String extra, Font font) {
        Color bg = new Color(238, 238, 248);
        table.addCell(tblCell(label,  font, bg, Element.ALIGN_LEFT));
        table.addCell(tblCell(amount, font, bg, Element.ALIGN_LEFT));
        table.addCell(tblCell(extra,  font, bg, Element.ALIGN_RIGHT));
    }

    private static PdfPCell tblCell(String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setBackgroundColor(bg);
        c.setPadding(6);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        c.setHorizontalAlignment(align);
        return c;
    }

    // ── Footer page event ─────────────────────────────────────────────────────

    private static class FooterEvent extends PdfPageEventHelper {
        private final String periodo;
        FooterEvent(String periodo) { this.periodo = periodo; }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Font f = fnt(FontFactory.HELVETICA, 8, TXT_LITE);
                Phrase left  = new Phrase("Generado por Faro · Asistente financiero personal", f);
                Phrase right = new Phrase(periodo + "  ·  pag. " + writer.getPageNumber(), f);
                ColumnText.showTextAligned(writer.getDirectContent(),
                        Element.ALIGN_LEFT,  left,  document.leftMargin(), 26, 0);
                ColumnText.showTextAligned(writer.getDirectContent(),
                        Element.ALIGN_RIGHT, right,
                        document.getPageSize().getWidth() - document.rightMargin(), 26, 0);
            } catch (Exception ignored) {}
        }
    }

    // ── Numeric helpers ───────────────────────────────────────────────────────

    private static double roundUpNice(double v) {
        if (v <= 0) return 10_000;
        double mag     = Math.pow(10, Math.floor(Math.log10(v)));
        double n       = v / mag;
        double rounded = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return rounded * mag;
    }

    // ── Font factory shorthand ────────────────────────────────────────────────

    private static Font fnt(String family, float size, Color color) {
        return FontFactory.getFont(family, "Cp1252", false, size, Font.NORMAL, color);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private static String fmtMoney(BigDecimal amount) {
        if (amount == null) return "$0";
        long v = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        String s = String.valueOf(Math.abs(v));
        StringBuilder r = new StringBuilder();
        int cnt = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (cnt > 0 && cnt % 3 == 0) r.insert(0, '.');
            r.insert(0, s.charAt(i));
            cnt++;
        }
        return (v < 0 ? "-$" : "$") + r;
    }

    private static String fmtMoneyShort(double v) {
        if (v >= 1_000_000) return String.format(Locale.US, "$%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format(Locale.US, "$%.0fk", v / 1_000);
        return String.format(Locale.US, "$%.0f", v);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
