package com.agentefinanciero.service;

import com.agentefinanciero.model.Gasto;
import com.agentefinanciero.repository.CategorizacionAprendidaRepository;
import com.agentefinanciero.repository.GastoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class BoletaService {

    private static final Logger log = LoggerFactory.getLogger(BoletaService.class);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIM   = 1500;

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    @Value("${twilio.account.sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token}")
    private String twilioAuthToken;

    private final GastoService gastoService;
    private final GastoRepository gastoRepository;
    private final CategorizacionAprendidaRepository categorizacionRepo;
    private final TwilioService twilioService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public BoletaService(GastoService gastoService,
                         GastoRepository gastoRepository,
                         CategorizacionAprendidaRepository categorizacionRepo,
                         TwilioService twilioService) {
        this.gastoService       = gastoService;
        this.gastoRepository    = gastoRepository;
        this.categorizacionRepo = categorizacionRepo;
        this.twilioService      = twilioService;
    }

    /** Entry point — called from the webhook virtual thread. */
    public void procesarYResponder(String from, String usuarioId,
                                   String mediaUrl, String contentType) {
        try {
            String respuesta = procesarBoleta(usuarioId, mediaUrl, contentType);
            twilioService.sendWhatsApp(from, respuesta);
        } catch (Exception e) {
            log.error("[Boleta] error para {}: {}", usuarioId, e.getMessage(), e);
            twilioService.sendWhatsApp(from,
                "Tuve un problema procesando tu boleta. Intenta de nuevo con otra foto 📸");
        }
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    private String procesarBoleta(String usuarioId, String mediaUrl, String contentType)
            throws Exception {

        byte[] imageBytes = descargarImagen(mediaUrl);
        log.info("[Boleta] descargado: {} bytes, tipo='{}'", imageBytes.length, contentType);

        String mediaType = resolverMediaType(contentType);
        if (imageBytes.length > MAX_BYTES) {
            log.info("[Boleta] imagen {}MB > 5MB, comprimiendo",
                imageBytes.length / 1024 / 1024);
            imageBytes = comprimirImagen(imageBytes);
            mediaType  = "image/jpeg";
        }

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String jsonStr = llamarClaudeVision(base64, mediaType);
        log.info("[Boleta] Claude: {}",
            jsonStr.length() > 200 ? jsonStr.substring(0, 200) + "…" : jsonStr);

        return guardarYFormatear(usuarioId, jsonStr);
    }

    // ── Descarga ──────────────────────────────────────────────────────────────

    private byte[] descargarImagen(String mediaUrl) throws Exception {
        String creds = twilioAccountSid + ":" + twilioAuthToken;
        String auth  = "Basic " + Base64.getEncoder()
                .encodeToString(creds.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(mediaUrl))
            .header("Authorization", auth)
            .GET()
            .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " descargando imagen de Twilio");
        return resp.body();
    }

    // ── Compresión ────────────────────────────────────────────────────────────

    private static String resolverMediaType(String contentType) {
        if (contentType == null)              return "image/jpeg";
        if (contentType.contains("png"))      return "image/png";
        if (contentType.contains("webp"))     return "image/webp";
        return "image/jpeg";
    }

    private static byte[] comprimirImagen(byte[] original) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
        if (img == null) return original;

        if (img.getWidth() > MAX_DIM || img.getHeight() > MAX_DIM) {
            double scale = Math.min((double) MAX_DIM / img.getWidth(),
                                    (double) MAX_DIM / img.getHeight());
            int w = (int)(img.getWidth()  * scale);
            int h = (int)(img.getHeight() * scale);
            BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            resized.createGraphics()
                   .drawImage(img.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
            img = resized;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.75f);
        writer.setOutput(ImageIO.createImageOutputStream(out));
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
        return out.toByteArray();
    }

    // ── Claude Vision ─────────────────────────────────────────────────────────

    private String llamarClaudeVision(String base64, String mediaType) throws Exception {
        String prompt = """
            Analiza esta boleta de compra y extrae:
            - Comercio (nombre del local). LEE EL NOMBRE CON CUIDADO y \
            transcríbelo exactamente como aparece en la boleta, sin inventar \
            ni modificar palabras. Por ejemplo, si dice "Unimarc Huepil" NO \
            escribas "Hueñil" ni ninguna variación — copia lo que ves.
            - Fecha
            - Lista de items con: descripción, monto, categoría sugerida
              (categorías válidas: Comida, Aseo, Salud, Hogar, Transporte, Entretenimiento, Otro)
            - Total

            Responde SOLO en JSON válido con este formato:
            {
              "comercio": "...",
              "fecha": "YYYY-MM-DD",
              "items": [
                {"descripcion": "...", "monto": 0, "categoria": "..."}
              ],
              "total": 0
            }

            Si la imagen no es una boleta, responde: {"error": "no_es_boleta"}""";

        // Build request body with explicit string keys (ObjectMapper snake_case applies to Java fields, not Map keys)
        Map<String, Object> requestBody = Map.of(
            "model",      "claude-sonnet-4-5",
            "max_tokens", 1024,
            "messages",   List.of(Map.of(
                "role",    "user",
                "content", List.of(
                    Map.of(
                        "type",   "image",
                        "source", Map.of(
                            "type",       "base64",
                            "media_type", mediaType,
                            "data",       base64
                        )
                    ),
                    Map.of("type", "text", "text", prompt)
                )
            ))
        );

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key",         anthropicApiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type",      "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Claude API HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root = mapper.readTree(resp.body());
        return root.path("content").get(0).path("text").asText();
    }

    // ── Parseo y guardado ─────────────────────────────────────────────────────

    private String guardarYFormatear(String usuarioId, String jsonStr) {
        try {
            // Strip markdown code fences if Claude wrapped the JSON
            String clean = jsonStr.strip();
            if (clean.startsWith("```")) {
                clean = clean.replaceAll("(?s)^```[a-z]*\\n?", "")
                             .replaceAll("(?s)```\\s*$", "")
                             .strip();
            }

            JsonNode boleta = mapper.readTree(clean);

            if (boleta.has("error")) {
                return "Hmm, no parece una boleta clara. ¿Puedes mandar otra foto con mejor luz? 📸";
            }

            String    comercio = boleta.path("comercio").asText("Comercio desconocido");
            LocalDate fecha    = parseFecha(boleta.path("fecha").asText(""));
            JsonNode  items    = boleta.path("items");

            if (!items.isArray() || items.isEmpty()) {
                return "No pude identificar items en la boleta. ¿Puedes mandar otra foto más clara? 📸";
            }

            List<Gasto> guardados = new ArrayList<>();
            StringBuilder lineas  = new StringBuilder();

            for (JsonNode item : items) {
                String desc   = item.path("descripcion").asText("Sin descripción");
                double montoD = item.path("monto").asDouble(0);
                if (montoD <= 0) {
                    log.warn("[Boleta] item ignorado (monto={}): '{}'", montoD, desc);
                    continue;
                }

                String cat = item.path("categoria").asText("Otro");

                // Override con categorización aprendida del usuario (best-effort)
                try {
                    String key = desc.toLowerCase().replaceAll("[^a-z0-9áéíóúüñ]", "");
                    cat = categorizacionRepo
                        .findByUsuarioIdAndDescripcionKey(usuarioId, key)
                        .map(c -> c.getCategoria())
                        .orElse(cat);
                } catch (Exception ex) {
                    log.warn("[Boleta] no se pudo consultar categorización aprendida: {}", ex.getMessage());
                }

                try {
                    Gasto g = gastoService.registrarMovimiento(
                        usuarioId, BigDecimal.valueOf(montoD), cat, desc, "gasto", fecha);
                    log.info("[Boleta] guardado: id={} descripcion='{}' monto={} categoria={} fecha={}",
                        g.getId(), desc, montoD, cat, fecha);
                    guardados.add(g);
                    lineas.append(String.format("- %s - $%,.0f (%s)%n", desc, montoD, cat));
                } catch (Exception ex) {
                    log.error("[Boleta] FALLO al guardar item '{}' monto={}: {}", desc, montoD, ex.getMessage(), ex);
                }
            }

            if (guardados.isEmpty()) {
                return "Pude leer la boleta pero los montos no eran claros. ¿Puedes intentar con otra foto? 📸";
            }

            // Total del mes actual
            LocalDate hoy      = LocalDate.now();
            LocalDate inicioM  = hoy.withDayOfMonth(1);
            BigDecimal totalMes = gastoRepository
                .findByUsuarioIdAndFechaBetweenOrderByFechaDescIdDesc(usuarioId, inicioM, hoy)
                .stream()
                .filter(g -> "gasto".equals(g.getTipo()))
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            double totalBoleta = boleta.path("total").asDouble(0);
            return String.format(
                "📸 Boleta procesada de %s:%n%s%nTotal: $%,.0f%n✅ Registrados %d gastos. Llevas $%,.0f este mes.",
                comercio,
                lineas.toString().strip(),
                totalBoleta,
                guardados.size(),
                totalMes.doubleValue());

        } catch (Exception e) {
            log.error("[Boleta] error en guardarYFormatear: {}", e.getMessage(), e);
            return "Pude ver la foto pero no pude leer la boleta claramente. ¿Puedes mandar otra foto? 📸";
        }
    }

    private static LocalDate parseFecha(String s) {
        if (s == null || s.isBlank()) return LocalDate.now();
        try { return LocalDate.parse(s); } catch (Exception e) { return LocalDate.now(); }
    }
}
