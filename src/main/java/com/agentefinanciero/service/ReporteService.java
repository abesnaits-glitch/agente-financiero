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
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    // Palette
    private static final Color DARK_BG  = new Color(30, 32, 41);
    private static final Color ACCENT   = new Color(127, 119, 221);
    private static final Color C_GREEN  = new Color(72, 185, 142);
    private static final Color C_RED    = new Color(215, 80, 80);
    private static final Color TXT_DARK = new Color(30, 32, 41);
    private static final Color TXT_MID  = new Color(90, 90, 110);
    private static final Color TXT_LITE = new Color(160, 160, 180);
    private static final Color ROW_ALT  = new Color(245, 245, 252);
    private static final Color BORDER   = new Color(218, 218, 232);

    @Value("${app.reports-dir:/tmp/faro-reports}")
    private String reportsDirPath;

    private final GastoService gastoService;
    private final UsuarioPerfilRepository perfilRepository;

    public ReporteService(GastoService gastoService, UsuarioPerfilRepository perfilRepository) {
        this.gastoService    = gastoService;
        this.perfilRepository = perfilRepository;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public String generarReporte(String usuarioId) {
        log.info("[Reporte] generando PDF para usuario '{}'", usuarioId);
        GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(usuarioId);
        UsuarioPerfil perfil = perfilRepository.findById(usuarioId).orElse(null);

        try {
            Path dir = Paths.get(reportsDirPath);
            Files.createDirectories(dir);

            // Delete old reports for this user
            String prefix = "reporte_" + usuarioId + "_";
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }

            LocalDate now = LocalDate.now();
            String filename = String.format("reporte_%s_%02d_%d.pdf",
                    usuarioId, now.getMonthValue(), now.getYear());
            Path file = dir.resolve(filename);

            buildPdf(file, resumen, perfil);

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
                          UsuarioPerfil perfil) throws Exception {

        LocalDate now     = LocalDate.now();
        String mesNombre  = capitalize(now.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")));
        String periodo    = mesNombre + " " + now.getYear();
        String nombre     = perfil != null && perfil.getNombre() != null ? perfil.getNombre() : null;
        BigDecimal presup = perfil != null ? perfil.getPresupuestoMensual() : null;

        Document doc = new Document(PageSize.A4, 48, 48, 30, 50);
        FileOutputStream fos = new FileOutputStream(file.toFile());
        PdfWriter writer = PdfWriter.getInstance(doc, fos);
        writer.setPageEvent(new FooterEvent(periodo));
        doc.open();

        try {
            // Fonts (CP1252 handles Spanish characters)
            Font fH18B = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  "Cp1252", false, 18, Font.NORMAL, Color.WHITE);
            Font fH13  = FontFactory.getFont(FontFactory.HELVETICA,       "Cp1252", false, 13, Font.NORMAL, TXT_LITE);
            Font fH11B = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  "Cp1252", false, 11, Font.NORMAL, TXT_DARK);
            Font fH10  = FontFactory.getFont(FontFactory.HELVETICA,       "Cp1252", false, 10, Font.NORMAL, TXT_LITE);
            Font fH9   = FontFactory.getFont(FontFactory.HELVETICA,       "Cp1252", false,  9, Font.NORMAL, TXT_MID);
            Font fH9B  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  "Cp1252", false,  9, Font.NORMAL, TXT_DARK);
            Font fH8   = FontFactory.getFont(FontFactory.HELVETICA,       "Cp1252", false,  8, Font.NORMAL, TXT_LITE);

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

            // ── Summary cards ───────────────────────────────────────────────────
            sectionTitle(doc, "Resumen del Mes", fH11B);
            BigDecimal balance = resumen.totalIngresado().subtract(resumen.totalGastado());
            boolean surplus    = balance.compareTo(BigDecimal.ZERO) >= 0;

            int numCards = presup != null ? 4 : 3;
            PdfPTable cards = new PdfPTable(numCards);
            cards.setWidthPercentage(100);
            cards.setSpacingAfter(18);

            addCard(cards, "INGRESOS DEL MES", fmtMoney(resumen.totalIngresado()), null,  C_GREEN, fH8);
            addCard(cards, "GASTOS DEL MES",   fmtMoney(resumen.totalGastado()),   null,  C_RED,   fH8);
            addCard(cards, "BALANCE",
                    (surplus ? "+" : "") + fmtMoney(balance), null,
                    surplus ? C_GREEN : C_RED, fH8);
            if (presup != null) {
                BigDecimal disp = presup.subtract(resumen.totalGastado());
                boolean over   = disp.compareTo(BigDecimal.ZERO) < 0;
                addCard(cards, "DISPONIBLE",
                        (over ? "-" : "") + fmtMoney(disp.abs()),
                        "de " + fmtMoney(presup),
                        over ? C_RED : C_GREEN, fH8);
            }
            doc.add(cards);

            // ── Category breakdown ──────────────────────────────────────────────
            Map<String, BigDecimal> gastosCat = resumen.movimientos().stream()
                    .filter(g -> "gasto".equals(g.getTipo()))
                    .collect(Collectors.groupingBy(
                            g -> g.getCategoria() != null ? capitalize(g.getCategoria()) : "Sin categoria",
                            Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

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
                    tblRow(catTbl, new String[]{
                            e.getKey(), fmtMoney(e.getValue()),
                            String.format(Locale.US, "%.1f%%", pct)
                    }, bg, fH9, new boolean[]{false, false, true});
                    alt = !alt;
                }
                // Total row
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

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM");
                boolean alt = false;
                for (Gasto g : resumen.movimientos()) {
                    boolean isGasto = "gasto".equals(g.getTipo());
                    Color amtColor = isGasto ? C_RED : C_GREEN;
                    Font amtFont = FontFactory.getFont(
                            FontFactory.HELVETICA_BOLD, "Cp1252", false, 9, Font.NORMAL, amtColor);
                    String montoStr = (isGasto ? "-" : "+") + fmtMoney(g.getMonto());
                    String desc = g.getDescripcion() != null && !g.getDescripcion().isBlank()
                            ? capitalize(g.getDescripcion())
                            : (g.getCategoria() != null ? capitalize(g.getCategoria()) : "-");
                    String cat  = g.getCategoria() != null ? capitalize(g.getCategoria()) : "-";
                    Color bg = alt ? ROW_ALT : Color.WHITE;

                    txTbl.addCell(tblCell(g.getFecha().format(dtf), fH9,   bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(desc,                     fH9,   bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(cat,                      fH9,   bg, Element.ALIGN_LEFT));
                    txTbl.addCell(tblCell(montoStr,                 amtFont, bg, Element.ALIGN_RIGHT));
                    alt = !alt;
                }
                doc.add(txTbl);
            }

        } finally {
            doc.close();
            fos.close();
        }
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
        Font vf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1252", false, 12, Font.NORMAL, accent);
        Font sf = FontFactory.getFont(FontFactory.HELVETICA,      "Cp1252", false,  8, Font.NORMAL, TXT_LITE);

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
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1252", false, 9, Font.NORMAL, Color.WHITE);
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
                Font f = FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, 8, Font.NORMAL, TXT_LITE);
                Phrase left  = new Phrase("Generado por Faro · Asistente financiero personal", f);
                Phrase right = new Phrase(periodo + "  ·  pag. " + writer.getPageNumber(), f);
                float y = 26;
                float mid = document.getPageSize().getWidth() / 2;
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,  left,  document.leftMargin(), y, 0);
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT, right, document.getPageSize().getWidth() - document.rightMargin(), y, 0);
            } catch (Exception ignored) {}
        }
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
