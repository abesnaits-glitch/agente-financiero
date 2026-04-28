package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final String[] FALLBACK_COLORS = {
        "#f97316", "#3b82f6", "#a855f7", "#22c55e", "#06b6d4",
        "#ef4444", "#ec4899", "#eab308", "#10b981", "#6b7280"
    };

    private static final Map<String, String> CAT_COLORS = new LinkedHashMap<>();
    private static final Map<String, String> CAT_EMOJIS = new LinkedHashMap<>();

    static {
        CAT_COLORS.put("comida",          "#f97316");
        CAT_COLORS.put("transporte",      "#3b82f6");
        CAT_COLORS.put("entretenimiento", "#a855f7");
        CAT_COLORS.put("salud",           "#22c55e");
        CAT_COLORS.put("servicios",       "#06b6d4");
        CAT_COLORS.put("vivienda",        "#ef4444");
        CAT_COLORS.put("ropa",            "#ec4899");
        CAT_COLORS.put("educacion",       "#eab308");
        CAT_COLORS.put("trabajo",         "#10b981");
        CAT_COLORS.put("otro",            "#6b7280");
        CAT_COLORS.put("sin categoria",   "#475569");

        CAT_EMOJIS.put("comida",          "🍔");
        CAT_EMOJIS.put("transporte",      "🚗");
        CAT_EMOJIS.put("entretenimiento", "🎮");
        CAT_EMOJIS.put("salud",           "💊");
        CAT_EMOJIS.put("servicios",       "💡");
        CAT_EMOJIS.put("vivienda",        "🏠");
        CAT_EMOJIS.put("ropa",            "👕");
        CAT_EMOJIS.put("educacion",       "📚");
        CAT_EMOJIS.put("trabajo",         "💼");
        CAT_EMOJIS.put("otro",            "📦");
        CAT_EMOJIS.put("sin categoria",   "❓");
    }

    // Placeholders: {mes_anio} {total_gastado} {balance_class} {balance_val}
    //               {pie_slices} {legend_items} {transaction_rows}
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body {
                width: 400px;
                background: #0f172a;
                color: #f1f5f9;
                font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
                padding: 24px 20px 36px;
            }
            .header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
            .logo {
                width: 40px; height: 40px;
                background: linear-gradient(135deg, #3b82f6, #8b5cf6);
                border-radius: 12px;
                display: flex; align-items: center; justify-content: center;
                font-size: 22px; flex-shrink: 0;
            }
            .header h1 { font-size: 18px; font-weight: 700; color: #f1f5f9; }
            .header p  { font-size: 12px; color: #64748b; margin-top: 2px; }
            .cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px; }
            .card {
                background: #1e293b; border-radius: 14px;
                padding: 16px; border: 1px solid #334155;
            }
            .card-label { font-size: 10px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.08em; margin-bottom: 8px; }
            .card-value { font-size: 20px; font-weight: 700; }
            .negative { color: #f87171; }
            .positive { color: #4ade80; }
            .section-title {
                font-size: 10px; font-weight: 600; color: #64748b;
                text-transform: uppercase; letter-spacing: 0.09em; margin-bottom: 12px;
            }
            .chart-section { margin-bottom: 20px; }
            .chart-box {
                background: #1e293b; border-radius: 14px;
                padding: 16px; border: 1px solid #334155;
                display: flex; align-items: center; gap: 14px;
            }
            .legend { flex: 1; min-width: 0; }
            .legend-item { display: flex; align-items: center; gap: 8px; margin-bottom: 9px; }
            .legend-item:last-child { margin-bottom: 0; }
            .legend-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }
            .legend-name { font-size: 12px; color: #cbd5e1; flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .legend-pct  { font-size: 11px; color: #94a3b8; font-weight: 600; flex-shrink: 0; }
            .tx-box {
                background: #1e293b; border-radius: 14px;
                padding: 14px 16px; border: 1px solid #334155;
            }
            .tx-item {
                display: flex; align-items: center;
                justify-content: space-between;
                padding: 9px 0; border-bottom: 1px solid #0f172a;
            }
            .tx-item:last-child  { border-bottom: none; padding-bottom: 0; }
            .tx-item:first-child { padding-top: 0; }
            .tx-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
            .tx-icon {
                width: 34px; height: 34px; border-radius: 9px;
                background: #0f172a; display: flex;
                align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0;
            }
            .tx-desc { font-size: 13px; font-weight: 500; color: #e2e8f0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 180px; }
            .tx-cat  { font-size: 11px; color: #475569; margin-top: 2px; }
            .gasto   { color: #f87171; font-size: 13px; font-weight: 700; flex-shrink: 0; }
            .ingreso { color: #4ade80; font-size: 13px; font-weight: 700; flex-shrink: 0; }
            .empty-state { text-align: center; color: #64748b; font-size: 12px; padding: 14px 0; }
            </style>
            </head>
            <body>
            <div class="header">
              <div class="logo">⚡</div>
              <div>
                <h1>Faro</h1>
                <p>Resumen · {mes_anio}</p>
              </div>
            </div>
            <div class="cards">
              <div class="card">
                <div class="card-label">Gastado este mes</div>
                <div class="card-value negative">{total_gastado}</div>
              </div>
              <div class="card">
                <div class="card-label">Balance</div>
                <div class="card-value {balance_class}">{balance_val}</div>
              </div>
            </div>
            <div class="chart-section">
              <div class="section-title">Por categoría</div>
              <div class="chart-box">
                <svg viewBox="0 0 200 200" width="140" height="140" style="flex-shrink:0">
                  {pie_slices}
                </svg>
                <div class="legend">
                  {legend_items}
                </div>
              </div>
            </div>
            <div>
              <div class="section-title">Últimos movimientos</div>
              <div class="tx-box">
                {transaction_rows}
              </div>
            </div>
            </body>
            </html>
            """;

    private final GastoService gastoService;
    private final Path imagesDir;

    @Value("${app.base-url}")
    private String baseUrl;

    public DashboardService(GastoService gastoService) throws IOException {
        this.gastoService = gastoService;
        this.imagesDir = Paths.get(System.getProperty("java.io.tmpdir"), "faro-images");
        Files.createDirectories(imagesDir);
    }

    public String generarDashboard(String usuarioId) throws Exception {
        GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(usuarioId);
        String html = buildHtml(resumen);

        String filename = "dashboard-" + usuarioId + "-" + System.currentTimeMillis() + ".png";
        Path outputPath = imagesDir.resolve(filename);

        renderHtmlToImage(html, outputPath);

        String url = baseUrl + "/images/" + filename;
        log.info("[Dashboard] imagen generada: {}", url);
        return url;
    }

    public Path getImagesDir() {
        return imagesDir;
    }

    private void renderHtmlToImage(String html, Path outputPath) {
        log.info("[Dashboard] iniciando Playwright para captura de screenshot...");
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.setViewportSize(400, 800);
            page.setContent(html);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(outputPath)
                    .setType(ScreenshotType.PNG)
                    .setFullPage(true));
            browser.close();
            log.info("[Dashboard] screenshot guardado en: {}", outputPath);
        }
    }

    private String buildHtml(GastoService.ResumenFinanciero resumen) {
        BigDecimal totalGastado  = resumen.totalGastado();
        BigDecimal totalIngresado = resumen.totalIngresado();
        BigDecimal balance = totalIngresado.subtract(totalGastado);

        Map<String, BigDecimal> gastosPorCat = resumen.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria() != null ? g.getCategoria() : "sin categoría",
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        PieChartResult chart = buildPieChartAndLegend(gastosPorCat, totalGastado);

        List<Gasto> ultimos = resumen.movimientos().stream().limit(5).toList();

        String mesAnio = capitalize(LocalDate.now()
                .getMonth().getDisplayName(TextStyle.FULL, new Locale("es")))
                + " " + LocalDate.now().getYear();

        String balanceClass = balance.compareTo(BigDecimal.ZERO) >= 0 ? "positive" : "negative";
        String transactionRows = ultimos.isEmpty()
                ? "<div class=\"empty-state\">Sin movimientos registrados</div>"
                : ultimos.stream().map(this::buildTransactionRow).collect(Collectors.joining());

        // Replace {balance_class} BEFORE {balance_val} to avoid substring collision
        return HTML_TEMPLATE
                .replace("{mes_anio}",        mesAnio)
                .replace("{total_gastado}",   formatPeso(totalGastado))
                .replace("{balance_class}",   balanceClass)
                .replace("{balance_val}",     formatPeso(balance))
                .replace("{pie_slices}",      chart.svgSlices())
                .replace("{legend_items}",    chart.legendHtml())
                .replace("{transaction_rows}", transactionRows);
    }

    // Builds an SVG donut chart using stroke-dasharray technique.
    // Each slice is a full circle with a single visible arc segment.
    // start angle: -90° = 12 o'clock; each slice rotated by cumulative degrees.
    private PieChartResult buildPieChartAndLegend(Map<String, BigDecimal> gastosPorCat,
                                                   BigDecimal totalGastado) {
        if (totalGastado.compareTo(BigDecimal.ZERO) == 0) {
            String svgEmpty = "<circle cx=\"100\" cy=\"100\" r=\"70\" fill=\"none\" stroke=\"#334155\" stroke-width=\"30\"/>"
                    + "<circle cx=\"100\" cy=\"100\" r=\"50\" fill=\"#1e293b\"/>"
                    + "<text x=\"100\" y=\"107\" text-anchor=\"middle\" fill=\"#64748b\" font-size=\"12\">Sin gastos</text>";
            String legendEmpty = "<div class=\"legend-item\"><span class=\"legend-name\" style=\"color:#64748b\">Sin gastos este mes</span></div>";
            return new PieChartResult(svgEmpty, legendEmpty);
        }

        double r = 70;
        double circumference = 2 * Math.PI * r;

        List<Map.Entry<String, BigDecimal>> entries = gastosPorCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        // Group categories beyond top-7 into "otro"
        if (entries.size() > 7) {
            BigDecimal restoSum = entries.subList(7, entries.size()).stream()
                    .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            entries = new ArrayList<>(entries.subList(0, 7));
            entries.add(Map.entry("otro", restoSum));
        }

        StringBuilder svgSlices  = new StringBuilder();
        StringBuilder legendHtml = new StringBuilder();
        double cumulativePct = 0;
        int colorIdx = 0;

        for (Map.Entry<String, BigDecimal> entry : entries) {
            double pct = entry.getValue()
                    .divide(totalGastado, 8, RoundingMode.HALF_UP)
                    .doubleValue();
            double sliceLen  = pct * circumference;
            double startAngle = -90 + (cumulativePct * 360);

            String catKey = normalize(entry.getKey());
            String color  = CAT_COLORS.getOrDefault(catKey,
                    FALLBACK_COLORS[colorIdx % FALLBACK_COLORS.length]);
            colorIdx++;

            if (pct > 0.005) {
                svgSlices.append(String.format(Locale.US,
                        "<circle cx=\"100\" cy=\"100\" r=\"%.1f\" fill=\"none\" stroke=\"%s\""
                        + " stroke-width=\"30\""
                        + " stroke-dasharray=\"%.4f %.4f\""
                        + " transform=\"rotate(%.4f 100 100)\"/>",
                        r, color, sliceLen, circumference - sliceLen, startAngle));
            }

            legendHtml.append(String.format(
                    "<div class=\"legend-item\">"
                    + "<span class=\"legend-dot\" style=\"background:%s\"></span>"
                    + "<span class=\"legend-name\">%s</span>"
                    + "<span class=\"legend-pct\">%.0f%%</span>"
                    + "</div>",
                    color, escapeHtml(capitalize(entry.getKey())), pct * 100));

            cumulativePct += pct;
        }

        // Inner hole to complete the donut
        svgSlices.append("<circle cx=\"100\" cy=\"100\" r=\"50\" fill=\"#1e293b\"/>");

        return new PieChartResult(svgSlices.toString(), legendHtml.toString());
    }

    private String buildTransactionRow(Gasto g) {
        String catKey     = normalize(g.getCategoria() != null ? g.getCategoria() : "otro");
        String emoji      = CAT_EMOJIS.getOrDefault(catKey, "📦");
        String catDisplay = g.getCategoria() != null ? capitalize(g.getCategoria()) : "Otro";
        String amountClass = "gasto".equals(g.getTipo()) ? "gasto" : "ingreso";
        String sign        = "gasto".equals(g.getTipo()) ? "-" : "+";
        String desc = (g.getDescripcion() != null && !g.getDescripcion().isBlank())
                ? capitalize(g.getDescripcion()) : catDisplay;
        String fecha = g.getFecha().format(DateTimeFormatter.ofPattern("d MMM", new Locale("es")));

        return "<div class=\"tx-item\">"
                + "<div class=\"tx-left\">"
                + "<div class=\"tx-icon\">" + emoji + "</div>"
                + "<div>"
                + "<div class=\"tx-desc\">" + escapeHtml(desc) + "</div>"
                + "<div class=\"tx-cat\">"  + escapeHtml(catDisplay) + " · " + fecha + "</div>"
                + "</div></div>"
                + "<div class=\"" + amountClass + "\">" + sign + formatPeso(g.getMonto()) + "</div>"
                + "</div>";
    }

    private static String formatPeso(BigDecimal amount) {
        boolean negative = amount.compareTo(BigDecimal.ZERO) < 0;
        String numStr = amount.abs().setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder formatted = new StringBuilder();
        int count = 0;
        for (int i = numStr.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) formatted.insert(0, '.');
            formatted.insert(0, numStr.charAt(i));
            count++;
        }
        return (negative ? "-$" : "$") + formatted;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String normalize(String s) {
        if (s == null) return "otro";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record PieChartResult(String svgSlices, String legendHtml) {}
}
