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

    // ── Palette ───────────────────────────────────────────────────────────────
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

    private static final Color[] BAR_COLORS = {
        ACCENT,
        C_GREEN,
        new Color(239, 159, 39),
        C_RED,
        new Color(77, 144, 214),
        new Color(160, 90, 180),
        new Color(60, 180, 180),
        new Color(200, 130, 60),
    };

    @Value("${app.reports-dir:/tmp/faro-reports}")
    private String reportsDirPath;

    private final GastoService gastoService;
    private final UsuarioPerfilRepository perfilRepository;
    private final ClaudeService claudeService;
    private final DashboardService dashboardService;

    public ReporteService(GastoService gastoService,
                          UsuarioPerfilRepository perfilRepository,
                          ClaudeService claudeService,
                          DashboardService dashboardService) {
        this.gastoService      = gastoService;
        this.perfilRepository  = perfilRepository;
        this.claudeService     = claudeService;
        this.dashboardService  = dashboardService;
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

            buildPdf(file, resumen, perfil, usuarioId, mes);

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
                          UsuarioPerfil perfil, String usuarioId, YearMonth mes) throws Exception {

        LocalDate now    = LocalDate.now();
        String mesNombre = capitalize(mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")));
        String periodo   = mesNombre + " " + mes.getYear();
        String nombre    = perfil != null && perfil.getNombre() != null ? perfil.getNombre() : null;
        BigDecimal presup = perfil != null ? perfil.getPresupuestoMensual() : null;

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
            hdr.setSpacingAfter(20);

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

            // ── Dashboard image ─────────────────────────────────────────────────
            try {
                Path dashPng = dashboardService.generarDashboardPath(usuarioId, mes);
                byte[] pngBytes = Files.readAllBytes(dashPng);
                Image dashImg = Image.getInstance(pngBytes);
                float usableWidth = doc.getPageSize().getWidth()
                        - doc.leftMargin() - doc.rightMargin();
                dashImg.scaleToFit(usableWidth, 9999);
                dashImg.setSpacingBefore(4);
                dashImg.setSpacingAfter(20);
                doc.add(dashImg);
                log.info("[Reporte] imagen del dashboard incluida en PDF");
            } catch (Exception e) {
                log.warn("[Reporte] no se pudo incluir imagen del dashboard: {}", e.getMessage());
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
            addCard(cards, "BALANCE",  (surplus ? "+" : "") + fmtMoney(balance), null,
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

            // ── Category bar chart ──────────────────────────────────────────────
            Map<String, BigDecimal> gastosCat = resumen.movimientos().stream()
                    .filter(g -> "gasto".equals(g.getTipo()))
                    .collect(Collectors.groupingBy(
                            g -> g.getCategoria() != null ? capitalize(g.getCategoria()) : "Sin categoria",
                            Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

            if (!gastosCat.isEmpty()) {
                addCategoryBarChart(doc, gastosCat, resumen.totalGastado(), fH9, fH9B, fH11B);
            }

            // ── Daily spending chart ────────────────────────────────────────────
            LocalDate endDate = mes.equals(YearMonth.now()) ? now : mes.atEndOfMonth();
            addDailyChart(doc, resumen.movimientos(), endDate, fH9, fH11B);

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

    // ── Chart: category bars ──────────────────────────────────────────────────

    private static void addCategoryBarChart(Document doc, Map<String, BigDecimal> gastosCat,
            BigDecimal totalGastado, Font fH9, Font fH9B, Font fH11B) throws DocumentException {

        List<Map.Entry<String, BigDecimal>> sorted = gastosCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8).toList();
        if (sorted.isEmpty()) return;

        BigDecimal maxVal = sorted.get(0).getValue();
        sectionTitle(doc, "Distribucion Visual de Gastos", fH11B);

        PdfPTable tbl = new PdfPTable(new float[]{2f, 3.5f, 1.5f, 1f});
        tbl.setWidthPercentage(100);
        tbl.setSpacingAfter(16);
        Font hf = fnt(FontFactory.HELVETICA_BOLD, 8, TXT_LITE);
        for (String h : new String[]{"Categoria", "Proporcion", "Monto", "%"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(HDR_ALT);
            c.setPadding(5);
            c.setBorderColor(BORDER);
            c.setBorderWidth(0.5f);
            tbl.addCell(c);
        }

        boolean alt = false;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, BigDecimal> e = sorted.get(i);
            float barPct = maxVal.compareTo(BigDecimal.ZERO) == 0 ? 2f
                    : e.getValue().divide(maxVal, 4, RoundingMode.HALF_UP).floatValue() * 100f;
            double totalPct = totalGastado.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : e.getValue().divide(totalGastado, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            Color bg = alt ? ROW_ALT : Color.WHITE;
            Color barColor = BAR_COLORS[i % BAR_COLORS.length];

            tbl.addCell(tblCell(e.getKey(), fH9, bg, Element.ALIGN_LEFT));
            tbl.addCell(barCell(barPct, barColor, bg));
            tbl.addCell(tblCell(fmtMoney(e.getValue()), fH9, bg, Element.ALIGN_RIGHT));
            tbl.addCell(tblCell(String.format(Locale.US, "%.0f%%", totalPct), fH9, bg, Element.ALIGN_RIGHT));
            alt = !alt;
        }
        doc.add(tbl);
    }

    // ── Chart: daily spending ─────────────────────────────────────────────────

    private static void addDailyChart(Document doc, List<Gasto> movimientos,
            LocalDate endDate, Font fH9, Font fH11B) throws DocumentException {

        LocalDate today  = endDate;
        LocalDate cutoff = today.minusDays(13);

        Map<LocalDate, BigDecimal> daily = movimientos.stream()
                .filter(g -> "gasto".equals(g.getTipo()) && !g.getFecha().isBefore(cutoff))
                .collect(Collectors.groupingBy(Gasto::getFecha,
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        List<Map.Entry<LocalDate, BigDecimal>> days = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            days.add(Map.entry(d, daily.getOrDefault(d, BigDecimal.ZERO)));
        }

        BigDecimal maxDay = days.stream().map(Map.Entry::getValue)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (maxDay.compareTo(BigDecimal.ZERO) == 0) return; // nothing to chart

        sectionTitle(doc, "Gasto Diario - Ultimos 14 Dias", fH11B);

        PdfPTable tbl = new PdfPTable(new float[]{1f, 4f, 1.8f});
        tbl.setWidthPercentage(100);
        tbl.setSpacingAfter(18);
        Font hf = fnt(FontFactory.HELVETICA_BOLD, 8, TXT_LITE);
        for (String h : new String[]{"Dia", "Gasto diario", "Monto"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(HDR_ALT);
            c.setPadding(5);
            c.setBorderColor(BORDER);
            c.setBorderWidth(0.5f);
            tbl.addCell(c);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");
        boolean alt = false;
        for (Map.Entry<LocalDate, BigDecimal> e : days) {
            BigDecimal v  = e.getValue();
            Color bg = alt ? ROW_ALT : Color.WHITE;
            boolean hasSpend = v.compareTo(BigDecimal.ZERO) > 0;
            float barPct = hasSpend
                    ? v.divide(maxDay, 4, RoundingMode.HALF_UP).floatValue() * 100f : 0f;

            tbl.addCell(tblCell(e.getKey().format(dtf), fH9, bg, Element.ALIGN_CENTER));
            tbl.addCell(hasSpend ? barCell(barPct, C_GREEN, bg) : tblCell("-", fH9, bg, Element.ALIGN_LEFT));
            tbl.addCell(tblCell(hasSpend ? fmtMoney(v) : "-", fH9, bg, Element.ALIGN_RIGHT));
            alt = !alt;
        }
        doc.add(tbl);
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

    // Renders a proportional colored bar using a nested locked-width table
    private static PdfPCell barCell(float fillPct, Color barColor, Color rowBg) {
        float fp = Math.max(2f, Math.min(98f, fillPct));
        float ep = 100f - fp;

        PdfPTable bar = new PdfPTable(new float[]{fp, ep});
        bar.setTotalWidth(160f);
        bar.setLockedWidth(true);

        PdfPCell filled = new PdfPCell(new Phrase(""));
        filled.setBackgroundColor(barColor);
        filled.setFixedHeight(10f);
        filled.setBorder(Rectangle.NO_BORDER);
        filled.setPadding(0);

        PdfPCell empty = new PdfPCell(new Phrase(""));
        empty.setBackgroundColor(new Color(232, 232, 242));
        empty.setFixedHeight(10f);
        empty.setBorder(Rectangle.NO_BORDER);
        empty.setPadding(0);

        bar.addCell(filled);
        bar.addCell(empty);

        PdfPCell container = new PdfPCell();
        container.addElement(bar);
        container.setVerticalAlignment(Element.ALIGN_MIDDLE);
        container.setPaddingTop(5);
        container.setPaddingBottom(5);
        container.setBackgroundColor(rowBg);
        container.setBorderColor(BORDER);
        container.setBorderWidth(0.5f);
        return container;
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

    // ── Font factory shorthand ─────────────────────────────────────────────────

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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
