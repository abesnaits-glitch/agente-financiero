package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.model.UsuarioPerfil;
import com.agentefinanciero.repository.UsuarioPerfilRepository;
import com.microsoft.playwright.*;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final Map<String, String> CAT_COLORS = new LinkedHashMap<>();
    private static final Map<String, String> CAT_EMOJIS = new LinkedHashMap<>();
    private static final String[] FALLBACK_COLORS = {
        "#7F77DD","#5DCAA5","#EF9F27","#D85A30","#888780",
        "#4A90D9","#E84393","#A8D05C","#F5A623","#9B59B6"
    };

    static {
        CAT_COLORS.put("arriendo",        "#7F77DD");
        CAT_COLORS.put("vivienda",        "#7F77DD");
        CAT_COLORS.put("comida",          "#5DCAA5");
        CAT_COLORS.put("transporte",      "#EF9F27");
        CAT_COLORS.put("entretenimiento", "#D85A30");
        CAT_COLORS.put("salud",           "#5DCAA5");
        CAT_COLORS.put("servicios",       "#7F77DD");
        CAT_COLORS.put("ropa",            "#D85A30");
        CAT_COLORS.put("educacion",       "#EF9F27");
        CAT_COLORS.put("trabajo",         "#5DCAA5");
        CAT_COLORS.put("otro",            "#888780");
        CAT_COLORS.put("sin categoria",   "#555555");

        CAT_EMOJIS.put("comida",          "🛒");
        CAT_EMOJIS.put("transporte",      "🚕");
        CAT_EMOJIS.put("entretenimiento", "🎬");
        CAT_EMOJIS.put("salud",           "💊");
        CAT_EMOJIS.put("servicios",       "💡");
        CAT_EMOJIS.put("vivienda",        "🏠");
        CAT_EMOJIS.put("arriendo",        "🏠");
        CAT_EMOJIS.put("ropa",            "👗");
        CAT_EMOJIS.put("educacion",       "📚");
        CAT_EMOJIS.put("trabajo",         "💼");
        CAT_EMOJIS.put("otro",            "📦");
        CAT_EMOJIS.put("sin categoria",   "❓");
    }

    @Value("${app.images-dir:/tmp/faro-images}")
    private String imagesDirPath;

    private final GastoService gastoService;
    private final UsuarioPerfilRepository perfilRepository;

    public DashboardService(GastoService gastoService, UsuarioPerfilRepository perfilRepository) {
        this.gastoService    = gastoService;
        this.perfilRepository = perfilRepository;
    }

    public String generarDashboard(String usuarioId) {
        return generarDashboard(usuarioId, YearMonth.now());
    }

    public String generarDashboard(String usuarioId, YearMonth mes) {
        log.info("[Dashboard] generando imagen para usuario '{}' mes={}", usuarioId, mes);
        String html     = generarHtml(usuarioId, mes);
        String filename = renderHtmlToImage(html, usuarioId);
        String base     = System.getenv("BASE_URL") != null ? System.getenv("BASE_URL") : "http://localhost:8080";
        return base + "/images/" + filename;
    }

    public Path generarDashboardPath(String usuarioId) {
        return generarDashboardPath(usuarioId, YearMonth.now());
    }

    public Path generarDashboardPath(String usuarioId, YearMonth mes) {
        String html     = generarHtml(usuarioId, mes);
        String filename = renderHtmlToImage(html, usuarioId);
        return Paths.get(imagesDirPath).resolve(filename);
    }

    private String renderHtmlToImage(String html, String usuarioId) {
        try {
            Path dir = Paths.get(imagesDirPath);
            Files.createDirectories(dir);

            // Eliminar imágenes anteriores del usuario
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("dashboard_" + usuarioId + "_"))
                      .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }

            String filename = "dashboard_" + usuarioId + "_" + System.currentTimeMillis() + ".png";
            Path file = dir.resolve(filename);

            try (Playwright playwright = Playwright.create()) {
                BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
                    .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage"
                    ));
                try (Browser browser = playwright.chromium().launch(opts)) {
                    Browser.NewContextOptions ctx = new Browser.NewContextOptions()
                        .setViewportSize(1200, 900)
                        .setDeviceScaleFactor(2.0);
                    try (BrowserContext context = browser.newContext(ctx)) {
                        Page page = context.newPage();
                        page.setContent(html, new Page.SetContentOptions().setTimeout(10_000));
                        page.screenshot(new Page.ScreenshotOptions()
                            .setPath(file)
                            .setType(ScreenshotType.PNG)
                            .setFullPage(false));
                    }
                }
            }
            log.info("[Dashboard] imagen guardada: {}", file);
            return file.getFileName().toString();
        } catch (IOException e) {
            throw new RuntimeException("Error generando imagen del dashboard", e);
        }
    }

    public String generarHtml(String usuarioId) {
        return generarHtml(usuarioId, YearMonth.now());
    }

    public String generarHtml(String usuarioId, YearMonth mes) {
        log.info("[Dashboard] generando HTML para usuario '{}' mes={}", usuarioId, mes);
        GastoService.ResumenFinanciero resumen = gastoService.obtenerResumen(usuarioId, mes);
        UsuarioPerfil perfil = perfilRepository.findById(usuarioId).orElse(null);
        return buildHtml(resumen, perfil, mes);
    }

    // ─── HTML assembly ────────────────────────────────────────────────────────

    private String buildHtml(GastoService.ResumenFinanciero resumen, UsuarioPerfil perfil,
                              YearMonth mes) {
        BigDecimal totalGastado   = resumen.totalGastado();
        BigDecimal totalIngresado = resumen.totalIngresado();
        BigDecimal presupuesto    = perfil != null ? perfil.getPresupuestoMensual() : null;

        Map<String, BigDecimal> gastosPorCat = resumen.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .collect(Collectors.groupingBy(
                        g -> g.getCategoria() != null ? g.getCategoria() : "sin categoría",
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        LocalDate endDate = mes.equals(YearMonth.now()) ? LocalDate.now() : mes.atEndOfMonth();
        LocalDate cutoff  = endDate.minusDays(13);
        Map<LocalDate, BigDecimal> gastosPorDia = resumen.movimientos().stream()
                .filter(g -> "gasto".equals(g.getTipo()) && !g.getFecha().isBefore(cutoff))
                .collect(Collectors.groupingBy(
                        Gasto::getFecha,
                        Collectors.reducing(BigDecimal.ZERO, Gasto::getMonto, BigDecimal::add)));

        int numTx = resumen.movimientos().size();

        String mesAnio = capitalize(mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("es")))
                + " " + mes.getYear();

        String metrics      = buildMetrics(totalIngresado, totalGastado, presupuesto, numTx);
        String donutChart   = buildDonutChart(gastosPorCat, totalGastado);
        String lineChart    = buildLineChart(gastosPorDia, endDate);
        String budgetBars   = buildBudgetBars(gastosPorCat, totalGastado, presupuesto);
        String transactions = buildTransactions(resumen.movimientos().stream().limit(5).toList());

        return "<!DOCTYPE html>\n"
            + "<html lang=\"es\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
            + "<title>Faro · Mis finanzas</title>\n"
            + "<style>\n"
            + "*{box-sizing:border-box;margin:0;padding:0;}\n"
            + "body{background:#161820;min-height:100vh;display:flex;justify-content:center;"
            +      "padding:20px 12px 40px;font-family:sans-serif;}\n"
            + ".wrap{width:100%;max-width:780px;}\n"
            + ".db{background:#1e2029;border-radius:16px;padding:24px;color:#ccc;}\n"
            + ".db-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;}\n"
            + ".db-title{font-size:16px;font-weight:500;color:#eee;}\n"
            + ".db-month{background:#252830;border:0.5px solid #3a3d4a;color:#aaa;"
            +           "border-radius:8px;padding:5px 10px;font-size:12px;}\n"
            + ".metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:18px;}\n"
            + ".metric{background:#252830;border-radius:10px;padding:14px 16px;border:0.5px solid #32364a;}\n"
            + ".m-label{font-size:11px;color:#666;margin-bottom:6px;}\n"
            + ".m-val{font-size:20px;font-weight:500;color:#eee;}\n"
            + ".m-sub{font-size:11px;margin-top:4px;}\n"
            + ".row2{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:12px;}\n"
            + ".card{background:#252830;border-radius:12px;padding:18px;border:0.5px solid #32364a;}\n"
            + ".card-title{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:500;"
            +             "color:#ddd;margin-bottom:3px;}\n"
            + ".card-sub{font-size:11px;color:#555;margin-bottom:14px;}\n"
            + ".sec-icon{width:22px;height:22px;border-radius:50%;display:flex;"
            +           "align-items:center;justify-content:center;flex-shrink:0;}\n"
            + ".txn-row{display:flex;align-items:center;justify-content:space-between;"
            +          "padding:8px 0;border-bottom:.5px solid #2e3140;}\n"
            + ".txn-row:last-child{border-bottom:none;}\n"
            + ".txn-ico{width:28px;height:28px;border-radius:7px;display:flex;"
            +          "align-items:center;justify-content:center;font-size:12px;"
            +          "margin-right:9px;flex-shrink:0;}\n"
            + ".txn-l{display:flex;align-items:center;}\n"
            + ".txn-name{font-size:12px;color:#ddd;}\n"
            + ".txn-date{font-size:11px;color:#444;margin-top:1px;}\n"
            + ".neg{color:#e07070;}.pos{color:#6ec97a;}\n"
            + ".ftr{text-align:center;margin-top:16px;font-size:11px;color:#333;}\n"
            + "@media(max-width:600px){"
            +   ".metrics{grid-template-columns:repeat(2,1fr);}"
            +   ".row2{grid-template-columns:1fr;}"
            +   ".m-val{font-size:17px;}"
            + "}\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"wrap\">\n"
            + "<div class=\"db\">\n"
            + "  <div class=\"db-header\">\n"
            + "    <span class=\"db-title\">⚡ Mis finanzas</span>\n"
            + "    <span class=\"db-month\">" + mesAnio + "</span>\n"
            + "  </div>\n"
            + metrics
            + "  <div class=\"row2\">\n"
            + donutChart
            + lineChart
            + "  </div>\n"
            + "  <div class=\"row2\">\n"
            + budgetBars
            + transactions
            + "  </div>\n"
            + "</div>\n"
            + "<div class=\"ftr\">Faro · agente financiero personal</div>\n"
            + "</div>\n"
            + "</body>\n"
            + "</html>";
    }

    // ─── Metrics row ──────────────────────────────────────────────────────────

    private String buildMetrics(BigDecimal totalIngresado, BigDecimal totalGastado,
                                 BigDecimal presupuesto, int numTx) {
        // Card 1: Ingresos del mes
        String c1 = "<div class=\"metric\"><div class=\"m-label\">Ingresos del mes</div>"
            + "<div class=\"m-val pos\">" + fmtK(totalIngresado.doubleValue()) + "</div></div>";

        // Card 2: Gastos del mes
        String c2 = "<div class=\"metric\"><div class=\"m-label\">Gastos del mes</div>"
            + "<div class=\"m-val neg\">" + fmtK(totalGastado.doubleValue()) + "</div></div>";

        // Card 3: Balance
        BigDecimal balance = totalIngresado.subtract(totalGastado);
        boolean positivo   = balance.compareTo(BigDecimal.ZERO) >= 0;
        String balanceVal  = (positivo ? "" : "-") + fmtK(balance.abs().doubleValue());
        String balanceCls  = positivo ? "pos" : "neg";
        String balanceSub  = positivo ? "superávit" : "déficit";
        String c3 = "<div class=\"metric\"><div class=\"m-label\">Balance</div>"
            + "<div class=\"m-val " + balanceCls + "\">" + balanceVal + "</div>"
            + "<div class=\"m-sub\" style=\"color:#555;\">" + balanceSub + "</div></div>";

        // Card 4: Transacciones
        String c4 = "<div class=\"metric\"><div class=\"m-label\">Transacciones</div>"
            + "<div class=\"m-val\">" + numTx + "</div>"
            + "<div class=\"m-sub\" style=\"color:#555;\">este mes</div></div>";

        return "<div class=\"metrics\">" + c1 + c2 + c3 + c4 + "</div>\n";
    }

    private static String metric(String label, String val, String subStyle, String subText) {
        String sub = subText.isEmpty() ? "" :
            "<div class=\"m-sub\" style=\"" + subStyle + "\">" + subText + "</div>";
        return "<div class=\"metric\"><div class=\"m-label\">" + label + "</div>"
             + "<div class=\"m-val\">" + val + "</div>" + sub + "</div>";
    }

    // ─── Donut chart ──────────────────────────────────────────────────────────
    // Uses SVG path arcs (same technique as v6) computed from real percentages.
    // Connector labels positioned left/right based on midpoint angle.

    private String buildDonutChart(Map<String, BigDecimal> gastosPorCat, BigDecimal totalGastado) {
        String header = "<div class=\"card\">"
            + "<div class=\"card-title\">"
            + "<div class=\"sec-icon\" style=\"background:#7F77DD;\">"
            + "<svg width=\"12\" height=\"12\" viewBox=\"0 0 12 12\" fill=\"none\">"
            + "<circle cx=\"6\" cy=\"6\" r=\"4\" stroke=\"#fff\" stroke-width=\"1.5\"/>"
            + "</svg></div>Por categoría</div>"
            + "<div class=\"card-sub\">Distribución del mes</div>";

        if (totalGastado.compareTo(BigDecimal.ZERO) == 0) {
            return header
                + "<svg viewBox=\"0 0 280 200\" width=\"100%\">"
                + "<circle cx=\"130\" cy=\"100\" r=\"78\" fill=\"none\" stroke=\"#2a2d3a\" stroke-width=\"26\"/>"
                + "<text x=\"130\" y=\"104\" text-anchor=\"middle\" font-size=\"11\" fill=\"#555\">Sin gastos</text>"
                + "</svg></div>";
        }

        // Limit to top 5; group remainder as "otro"
        List<Map.Entry<String, BigDecimal>> entries = gastosPorCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        if (entries.size() > 5) {
            BigDecimal resto = entries.subList(5, entries.size()).stream()
                    .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            entries = new ArrayList<>(entries.subList(0, 5));
            entries.add(Map.entry("otro", resto));
        }

        double cx = 130, cy = 100, rOut = 78, rIn = 52;
        StringBuilder slices   = new StringBuilder();
        StringBuilder pctTexts = new StringBuilder();

        List<LabelInfo> rightLabels = new ArrayList<>();
        List<LabelInfo> leftLabels  = new ArrayList<>();

        // Pre-compute visual sweeps — minimum 5% so thin slices stay visible.
        double minSweepRad = 2 * Math.PI * 0.05;
        double[] visSweeps = new double[entries.size()];
        {
            double totalVis = 0;
            for (int k = 0; k < entries.size(); k++) {
                double p = entries.get(k).getValue().divide(totalGastado, 8, RoundingMode.HALF_UP).doubleValue();
                visSweeps[k] = (p > 0.002) ? Math.max(p * 2 * Math.PI, minSweepRad) : 0;
                totalVis += visSweeps[k];
            }
            if (totalVis > 2 * Math.PI + 0.001) {
                double scale = (2 * Math.PI) / totalVis;
                for (int k = 0; k < visSweeps.length; k++) visSweeps[k] *= scale;
            }
        }

        double angle = -Math.PI / 2; // start at 12 o'clock
        int ci = 0;

        for (int idx = 0; idx < entries.size(); idx++) {
            Map.Entry<String, BigDecimal> e = entries.get(idx);
            double pct   = e.getValue().divide(totalGastado, 8, RoundingMode.HALF_UP).doubleValue();
            double sweep = visSweeps[idx];
            double endA  = angle + sweep;
            int    large = sweep > Math.PI ? 1 : 0;

            String color = CAT_COLORS.getOrDefault(norm(e.getKey()),
                    FALLBACK_COLORS[ci % FALLBACK_COLORS.length]);
            ci++;

            if (sweep < 0.01) { continue; }

            // For a 100% slice the start == end point breaks the arc; draw full circle instead
            if (pct > 0.9995) {
                slices.append(String.format(Locale.US,
                    "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" stroke=\"#161820\" stroke-width=\"2\"/>",
                    cx, cy, rOut, color));
            } else {
                double x1 = cx + rOut * Math.cos(angle),  y1 = cy + rOut * Math.sin(angle);
                double x2 = cx + rOut * Math.cos(endA),   y2 = cy + rOut * Math.sin(endA);
                double xi1= cx + rIn  * Math.cos(angle),  yi1= cy + rIn  * Math.sin(angle);
                double xi2= cx + rIn  * Math.cos(endA),   yi2= cy + rIn  * Math.sin(endA);
                slices.append(String.format(Locale.US,
                    "<path d=\"M%.2f %.2f A%.0f %.0f 0 %d 1 %.2f %.2f "
                    + "L%.2f %.2f A%.0f %.0f 0 %d 0 %.2f %.2fZ\" fill=\"%s\" stroke=\"#161820\" stroke-width=\"2\"/>",
                    x1, y1, rOut, rOut, large, x2, y2,
                    xi2, yi2, rIn, rIn, large, xi1, yi1, color));
            }

            // % inside slice (only if wide enough)
            double midA = angle + sweep / 2;
            if (pct > 0.09) {
                double tr = (rOut + rIn) / 2;
                pctTexts.append(String.format(Locale.US,
                    "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"9\" font-weight=\"500\" fill=\"#fff\">%.0f%%</text>",
                    cx + tr * Math.cos(midA), cy + tr * Math.sin(midA) + 3, pct * 100));
            }

            // Connector label dot at radius 85
            double dR = 85;
            double dX = cx + dR * Math.cos(midA);
            double dY = cy + dR * Math.sin(midA);
            LabelInfo li = new LabelInfo(dX, dY, color, capitalize(e.getKey()), pct);
            if (Math.cos(midA) >= 0) rightLabels.add(li);
            else                     leftLabels.add(li);

            angle = endA;
        }

        spreadLabels(rightLabels);
        spreadLabels(leftLabels);

        StringBuilder labels = new StringBuilder();
        for (LabelInfo li : rightLabels) {
            labels.append(String.format(Locale.US,
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"215\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"0.8\"/>"
                + "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"2\" fill=\"%s\"/>"
                + "<text x=\"218\" y=\"%.1f\" font-size=\"9.5\" font-weight=\"500\" fill=\"%s\">%s</text>"
                + "<text x=\"218\" y=\"%.1f\" font-size=\"8.5\" fill=\"#555\">%.0f%%</text>",
                li.dX, li.dY, li.lY, li.color,
                li.dX, li.dY, li.color,
                li.lY - 1, li.color, escHtml(li.name),
                li.lY + 9, li.pct * 100));
        }
        for (LabelInfo li : leftLabels) {
            labels.append(String.format(Locale.US,
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"45\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"0.8\"/>"
                + "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"2\" fill=\"%s\"/>"
                + "<text x=\"42\" y=\"%.1f\" font-size=\"9.5\" font-weight=\"500\" fill=\"%s\" text-anchor=\"end\">%s</text>"
                + "<text x=\"42\" y=\"%.1f\" font-size=\"8.5\" fill=\"#555\" text-anchor=\"end\">%.0f%%</text>",
                li.dX, li.dY, li.lY, li.color,
                li.dX, li.dY, li.color,
                li.lY - 1, li.color, escHtml(li.name),
                li.lY + 9, li.pct * 100));
        }

        String centerTotal = fmtK(totalGastado.doubleValue());

        return header
            + "<svg viewBox=\"0 0 280 200\" width=\"100%\" xmlns=\"http://www.w3.org/2000/svg\">"
            + "<circle cx=\"130\" cy=\"100\" r=\"95\" fill=\"#161820\"/>"
            + "<circle cx=\"130\" cy=\"100\" r=\"95\" fill=\"none\" stroke=\"#2a2d3a\" stroke-width=\"0.5\"/>"
            + "<circle cx=\"130\" cy=\"100\" r=\"78\" fill=\"none\" stroke=\"#2a2d3a\" stroke-width=\"0.5\"/>"
            + "<circle cx=\"130\" cy=\"100\" r=\"62\" fill=\"none\" stroke=\"#2a2d3a\" stroke-width=\"0.5\"/>"
            + "<circle cx=\"130\" cy=\"100\" r=\"46\" fill=\"none\" stroke=\"#2a2d3a\" stroke-width=\"0.5\"/>"
            + slices
            + pctTexts
            + "<circle cx=\"130\" cy=\"100\" r=\"52\" fill=\"#1e2029\"/>"
            + "<text x=\"130\" y=\"105\" text-anchor=\"middle\" font-size=\"14\" font-weight=\"500\" fill=\"#eee\">"
            + centerTotal + "</text>"
            + "<text x=\"130\" y=\"119\" text-anchor=\"middle\" font-size=\"9\" fill=\"#666\">total</text>"
            + labels
            + "</svg></div>";
    }

    // ─── Line chart ───────────────────────────────────────────────────────────

    private String buildLineChart(Map<LocalDate, BigDecimal> gastosPorDia, LocalDate endDate) {
        LocalDate today = endDate;
        LocalDate[] days = new LocalDate[14];
        double[] vals = new double[14];
        for (int i = 0; i < 14; i++) {
            days[i] = today.minusDays(13 - i);
            BigDecimal v = gastosPorDia.get(days[i]);
            vals[i] = v != null ? v.doubleValue() : 0;
        }

        double maxVal = Arrays.stream(vals).max().orElse(0);
        if (maxVal == 0) maxVal = 10000;
        maxVal = roundUpNice(maxVal);

        // SVG plot area: x=[30,270], y=[20,119]
        double xS = 30, xE = 270, yT = 20, yB = 119;
        double xStep = (xE - xS) / 13.0;

        StringBuilder ptsBuf  = new StringBuilder(); // polyline
        StringBuilder polyBuf = new StringBuilder(); // polygon for fill area
        StringBuilder dotsBuf = new StringBuilder();
        StringBuilder xLbls   = new StringBuilder();

        polyBuf.append(String.format(Locale.US, "%.1f,%.1f ", xS, yB));

        for (int i = 0; i < 14; i++) {
            double x = xS + i * xStep;
            double y = yB - (vals[i] / maxVal) * (yB - yT);
            ptsBuf.append(String.format(Locale.US, "%.1f,%.1f ", x, y));
            polyBuf.append(String.format(Locale.US, "%.1f,%.1f ", x, y));
            if (vals[i] > 0) {
                dotsBuf.append(String.format(Locale.US,
                    "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"2.5\" fill=\"#5DCAA5\"/>", x, y));
            }
            // Show day label every other point to avoid crowding
            if (i % 2 == 0 || i == 13) {
                xLbls.append(String.format(Locale.US,
                    "<text x=\"%.1f\" y=\"135\" text-anchor=\"middle\" font-size=\"9\" fill=\"#444\">%d</text>",
                    x, days[i].getDayOfMonth()));
            }
        }
        polyBuf.append(String.format(Locale.US, "%.1f,%.1f", xE, yB));

        return "<div class=\"card\">"
            + "<div class=\"card-title\">"
            + "<div class=\"sec-icon\" style=\"background:#5DCAA5;\">"
            + "<svg width=\"12\" height=\"12\" viewBox=\"0 0 12 12\" fill=\"none\">"
            + "<polyline points=\"1,9 4,5 7,7 11,2\" stroke=\"#fff\" stroke-width=\"1.5\""
            + " fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
            + "</svg></div>Gasto diario</div>"
            + "<div class=\"card-sub\">Últimos 14 días</div>"
            + "<svg viewBox=\"0 0 280 160\" width=\"100%\" xmlns=\"http://www.w3.org/2000/svg\">"
            + "<line x1=\"30\" y1=\"20\"  x2=\"270\" y2=\"20\"  stroke=\"rgba(255,255,255,0.04)\" stroke-width=\"1\"/>"
            + "<line x1=\"30\" y1=\"53\"  x2=\"270\" y2=\"53\"  stroke=\"rgba(255,255,255,0.04)\" stroke-width=\"1\"/>"
            + "<line x1=\"30\" y1=\"86\"  x2=\"270\" y2=\"86\"  stroke=\"rgba(255,255,255,0.04)\" stroke-width=\"1\"/>"
            + "<line x1=\"30\" y1=\"119\" x2=\"270\" y2=\"119\" stroke=\"rgba(255,255,255,0.04)\" stroke-width=\"1\"/>"
            + "<text x=\"26\" y=\"23\"  text-anchor=\"end\" font-size=\"9\" fill=\"#444\">" + fmtK(maxVal)           + "</text>"
            + "<text x=\"26\" y=\"56\"  text-anchor=\"end\" font-size=\"9\" fill=\"#444\">" + fmtK(maxVal * 0.667)   + "</text>"
            + "<text x=\"26\" y=\"89\"  text-anchor=\"end\" font-size=\"9\" fill=\"#444\">" + fmtK(maxVal * 0.333)   + "</text>"
            + "<text x=\"26\" y=\"122\" text-anchor=\"end\" font-size=\"9\" fill=\"#444\">$0</text>"
            + "<defs><linearGradient id=\"lg\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">"
            + "<stop offset=\"0%\" stop-color=\"#5DCAA5\" stop-opacity=\"0.2\"/>"
            + "<stop offset=\"100%\" stop-color=\"#5DCAA5\" stop-opacity=\"0\"/>"
            + "</linearGradient></defs>"
            + "<polygon points=\"" + polyBuf + "\" fill=\"url(#lg)\"/>"
            + "<polyline points=\"" + ptsBuf + "\" fill=\"none\" stroke=\"#5DCAA5\""
            + " stroke-width=\"2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>"
            + dotsBuf
            + xLbls
            + "</svg></div>";
    }

    // ─── Budget bars ──────────────────────────────────────────────────────────

    private String buildBudgetBars(Map<String, BigDecimal> gastosPorCat,
                                    BigDecimal totalGastado, BigDecimal presupuesto) {
        List<Map.Entry<String, BigDecimal>> top5 = gastosPorCat.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5).toList();

        boolean hasPresupuesto = presupuesto != null && presupuesto.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal denom = hasPresupuesto ? presupuesto : totalGastado;

        // Build budget summary line
        String summaryHtml;
        if (hasPresupuesto) {
            BigDecimal disponible = presupuesto.subtract(totalGastado);
            boolean over = disponible.compareTo(BigDecimal.ZERO) < 0;
            double usedPct = Math.min(100, totalGastado.divide(presupuesto, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100);
            String dispColor = over ? "#e07070" : "#6ec97a";
            String dispLabel = over
                ? "−" + fmtK(disponible.abs().doubleValue()) + " excedido"
                : fmtK(disponible.doubleValue()) + " disponible";
            summaryHtml = "<div style=\"display:flex;justify-content:space-between;align-items:center;"
                + "background:#1a1c25;border-radius:8px;padding:8px 10px;margin-bottom:10px;\">"
                + "<span style=\"font-size:10px;color:#555;\">Gastado: <b style=\"color:#e07070;\">"
                + fmtK(totalGastado.doubleValue()) + "</b> de " + fmtK(presupuesto.doubleValue())
                + String.format(Locale.US, " (%.0f%%)", usedPct) + "</span>"
                + "<span style=\"font-size:10px;font-weight:500;color:" + dispColor + ";\">"
                + dispLabel + "</span></div>";
        } else {
            summaryHtml = "";
        }

        String subLabel = hasPresupuesto ? "Uso por categoría" : "Distribución del gasto";

        String header = "<div class=\"card\">"
            + "<div class=\"card-title\">"
            + "<div class=\"sec-icon\" style=\"background:#D85A30;\">"
            + "<svg width=\"12\" height=\"12\" viewBox=\"0 0 12 12\" fill=\"none\">"
            + "<rect x=\"1\" y=\"5\" width=\"10\" height=\"2\" rx=\"1\" fill=\"#fff\"/>"
            + "<rect x=\"1\" y=\"2\" width=\"7\"  height=\"2\" rx=\"1\" fill=\"#fff\"/>"
            + "<rect x=\"1\" y=\"8\" width=\"5\"  height=\"2\" rx=\"1\" fill=\"#fff\"/>"
            + "</svg></div>Presupuesto</div>"
            + "<div class=\"card-sub\">" + subLabel + "</div>"
            + summaryHtml;

        if (top5.isEmpty() || denom.compareTo(BigDecimal.ZERO) == 0) {
            return header + "<p style=\"color:#444;font-size:12px;padding:10px 0;\">Sin datos</p></div>";
        }

        int rowH = 28, svgH = top5.size() * rowH + 10;
        double barW = 160, barX = 72;
        StringBuilder bars = new StringBuilder();
        int ci = 0;

        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, BigDecimal> e = top5.get(i);
            double pct      = Math.min(1.05, e.getValue().divide(denom, 8, RoundingMode.HALF_UP).doubleValue());
            double fillW    = Math.min(barW, pct * barW);
            int    yBaseline = i * rowH + 20;
            boolean over    = pct >= 1.0;

            String color    = CAT_COLORS.getOrDefault(norm(e.getKey()),
                    FALLBACK_COLORS[ci % FALLBACK_COLORS.length]);
            String pctColor = over ? "#e07070" : "#555";
            ci++;

            String label = capitalize(e.getKey());
            if (label.length() > 10) label = label.substring(0, 9) + "…";

            String amtLabel = fmtK(e.getValue().doubleValue());
            String denomLabel = hasPresupuesto ? "/" + fmtK(presupuesto.doubleValue()) : "";

            bars.append(String.format(Locale.US,
                "<text x=\"66\" y=\"%d\" text-anchor=\"end\" font-size=\"10\" font-weight=\"500\" fill=\"%s\">%s</text>"
                + "<rect x=\"%.0f\" y=\"%d\" width=\"%.0f\" height=\"7\" rx=\"3\" fill=\"#1a1c25\"/>"
                + "<rect x=\"%.0f\" y=\"%d\" width=\"%.1f\" height=\"7\" rx=\"3\" fill=\"%s\"/>"
                + "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"5\" fill=\"%s\" stroke=\"#252830\" stroke-width=\"2\"/>"
                + "<text x=\"240\" y=\"%d\" font-size=\"9.5\" fill=\"%s\">%.0f%%</text>"
                + "<text x=\"258\" y=\"%d\" font-size=\"9\" fill=\"#444\">%s</text>",
                yBaseline, color, escHtml(label),
                barX, yBaseline - 6, barW,
                barX, yBaseline - 6, fillW, color,
                barX + fillW, (double)(yBaseline - 6) + 3.5, color,
                yBaseline, pctColor, pct * 100,
                yBaseline, amtLabel + denomLabel));

            if (over) {
                bars.append(String.format(Locale.US,
                    "<line x1=\"%.0f\" y1=\"%d\" x2=\"%.0f\" y2=\"%d\""
                    + " stroke=\"%s\" stroke-width=\"1.5\" stroke-dasharray=\"2 2\"/>",
                    barX + barW, yBaseline - 9, barX + barW, yBaseline + 2, color));
            }
        }

        return header
            + "<svg viewBox=\"0 0 300 " + svgH + "\" width=\"100%\" xmlns=\"http://www.w3.org/2000/svg\">"
            + bars
            + "</svg></div>";
    }

    // ─── Transactions ─────────────────────────────────────────────────────────

    private String buildTransactions(List<Gasto> movimientos) {
        String header = "<div class=\"card\">"
            + "<div class=\"card-title\">"
            + "<div class=\"sec-icon\" style=\"background:#7F77DD;\">"
            + "<svg width=\"12\" height=\"12\" viewBox=\"0 0 12 12\" fill=\"none\">"
            + "<rect x=\"1\" y=\"1\" width=\"10\" height=\"10\" rx=\"2\""
            +   " stroke=\"#fff\" stroke-width=\"1.2\" fill=\"none\"/>"
            + "<line x1=\"4\" y1=\"1\" x2=\"4\" y2=\"11\" stroke=\"#fff\" stroke-width=\"1\"/>"
            + "</svg></div>Últimas transacciones</div>"
            + "<div class=\"card-sub\">Registradas vía WhatsApp</div>";

        if (movimientos.isEmpty()) {
            return header + "<div style=\"color:#444;font-size:12px;padding:16px 0;text-align:center;\">Sin movimientos</div></div>";
        }

        StringBuilder rows = new StringBuilder();
        for (Gasto g : movimientos) {
            boolean isGasto = "gasto".equals(g.getTipo());
            String catKey   = norm(g.getCategoria() != null ? g.getCategoria() : "otro");
            String emoji    = CAT_EMOJIS.getOrDefault(catKey, "📦");
            String catDisp  = g.getCategoria() != null ? capitalize(g.getCategoria()) : "Otro";
            String desc     = (g.getDescripcion() != null && !g.getDescripcion().isBlank())
                    ? capitalize(g.getDescripcion()) : catDisp;
            if (desc.length() > 22) desc = desc.substring(0, 20) + "…";
            String dateStr  = fmtDate(g.getFecha());
            String sign     = isGasto ? "−" : "+";
            String amtCls   = isGasto ? "neg" : "pos";
            String iconBg   = isGasto ? "#1a2030" : "#1a2d1e";
            String amount   = sign + "$" + fmtNumber(g.getMonto());

            rows.append("<div class=\"txn-row\">"
                + "<div class=\"txn-l\">"
                + "<div class=\"txn-ico\" style=\"background:" + iconBg + ";\">" + emoji + "</div>"
                + "<div>"
                + "<div class=\"txn-name\">" + escHtml(desc) + "</div>"
                + "<div class=\"txn-date\">" + dateStr + " · " + escHtml(catDisp) + "</div>"
                + "</div></div>"
                + "<span style=\"font-size:13px;font-weight:500;\" class=\"" + amtCls + "\">" + amount + "</span>"
                + "</div>");
        }

        return header + rows + "</div>";
    }

    // ─── SVG helpers ──────────────────────────────────────────────────────────

    private static void spreadLabels(List<LabelInfo> labels) {
        labels.sort(Comparator.comparingDouble(l -> l.dY));
        for (int i = 1; i < labels.size(); i++) {
            if (labels.get(i).lY - labels.get(i - 1).lY < 20) {
                labels.get(i).lY = labels.get(i - 1).lY + 20;
            }
        }
        // Clamp to SVG bounds
        for (LabelInfo li : labels) li.lY = Math.max(15, Math.min(190, li.lY));
    }

    private static double roundUpNice(double v) {
        if (v <= 0) return 10_000;
        double mag = Math.pow(10, Math.floor(Math.log10(v)));
        double n   = v / mag;
        double rounded = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return rounded * mag;
    }

    // ─── Formatting helpers ───────────────────────────────────────────────────

    private static String fmtK(double v) {
        if (v < 0) return "-" + fmtK(-v);
        if (v >= 1_000_000) {
            double m = v / 1_000_000;
            return "$" + (m == Math.floor(m) ? String.valueOf((long) m)
                    : String.format(Locale.US, "%.1f", m)) + "M";
        }
        if (v >= 1_000) {
            double k = v / 1_000;
            return "$" + (k == Math.floor(k) ? String.valueOf((long) k)
                    : String.format(Locale.US, "%.1f", k)) + "k";
        }
        return "$" + Math.round(v);
    }

    private static String fmtNumber(BigDecimal amount) {
        String s = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        StringBuilder r = new StringBuilder();
        int cnt = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (cnt > 0 && cnt % 3 == 0) r.insert(0, '.');
            r.insert(0, s.charAt(i));
            cnt++;
        }
        return r.toString();
    }

    private static String fmtDate(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today))              return "Hoy";
        if (d.equals(today.minusDays(1))) return "Ayer";
        return d.format(DateTimeFormatter.ofPattern("d MMM", new Locale("es")));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String norm(String s) {
        if (s == null) return "otro";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ─── Label helper ─────────────────────────────────────────────────────────

    private static class LabelInfo {
        final double dX, dY;
        double lY;
        final String color, name;
        final double pct;
        LabelInfo(double dX, double dY, String color, String name, double pct) {
            this.dX = dX; this.dY = dY; this.lY = dY;
            this.color = color; this.name = name; this.pct = pct;
        }
    }
}
