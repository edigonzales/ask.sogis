package ch.so.agi.ask.mcp;

import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ch.so.agi.ask.mcp.ToolResult.Status;
import ch.so.agi.ask.mcp.McpToolArgSchema;
import ch.so.agi.ask.config.LandregPrintProperties;
import ch.so.agi.ask.mcp.PrintFileStorage;

@Component
public class ProcessingTools {
    private static final Logger log = LoggerFactory.getLogger(ProcessingTools.class);
    
    private static final String BASE_URL = "https://geo.so.ch/api/v1/featureinfo/somap";
    private static final String LAYER_NAME = "ch.so.afu.ewsabfrage.abfrage";
    private static final int IMAGE_SIZE = 101;
    private static final int CENTER_PIXEL = (IMAGE_SIZE + 1) / 2; // 51 für 101px
    private static final DecimalFormat DECIMAL_FORMAT = decimalFormatter();
    private static final Pattern HREF_PATTERN = Pattern.compile("href=['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TEXT_PATTERN = Pattern.compile(">([^<]+)<");

    private final RestClient geothermalClient;
    private final RestClient landregPrintClient;
    private final LandregPrintProperties landregPrintProperties;
    private final PrintFileStorage printFileStorage;

    public ProcessingTools(RestClient.Builder restClientBuilder, LandregPrintProperties landregPrintProperties,
            PrintFileStorage printFileStorage) {
        this.geothermalClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.landregPrintClient = restClientBuilder.baseUrl(landregPrintProperties.getService()).build();
        this.landregPrintProperties = landregPrintProperties;
        this.printFileStorage = printFileStorage;
    }

    public record ProcessingResult(Status status, List<Map<String, Object>> items, String message)
            implements ToolResult {
    }

    public record ParsedFeature(String resultText, LinkInfo linkInfo) {
    }

    public record LinkInfo(String href, String label) {
        String displayLabel() {
            if (label != null && !label.isBlank()) {
                return label;
            }
            if (href != null && href.length() > "https://".length()) {
                return href;
            }
            return "PDF";
        }
    }

    @McpTool(name = "processing.getGeothermalBoreInfoByXY", description = "Checks geothermal probe feasibility at LV95 coordinates.")
    public ProcessingResult getGeothermalBoreInfoByXY(
            @McpToolParam(description = "Coordinate input, expecting keys 'x' and 'y' or 'coord' array. Optional 'resolution' (m/px).", required = true)
            @McpToolArgSchema("{ 'x': 'number - LV95 east', 'y': 'number - LV95 north', 'coord': '[east, north]', 'resolution': 'number - map resolution in meters per pixel (optional)' }")
            Map<String, Object> args) {
        Double x = asDouble(args.get("x"), null);
        Double y = asDouble(args.get("y"), null);
        List<Double> coord = args.containsKey("coord") && args.get("coord") instanceof List<?> raw
                ? raw.stream().map(o -> asDouble(o, null)).filter(Objects::nonNull).toList()
                : (x != null && y != null ? List.of(x, y) : List.of());

        if (coord.size() < 2 || coord.stream().anyMatch(Objects::isNull)) {
            return new ProcessingResult(Status.ERROR, List.of(), "Ungültige Koordinate übergeben.");
        }

        double resolution = Optional.ofNullable(asDouble(args.get("resolution"), null)).filter(r -> r > 0d)
                .orElse(1d);
        String bbox = buildBbox(coord.get(0), coord.get(1), resolution);

        try {
            var uri = UriComponentsBuilder.fromUriString(BASE_URL)
                    .queryParam("SERVICE", "WMS")
                    .queryParam("REQUEST", "GetFeatureInfo")
                    .queryParam("VERSION", "1.3.0")
                    .queryParam("LAYERS", LAYER_NAME)
                    .queryParam("QUERY_LAYERS", LAYER_NAME)
                    .queryParam("STYLES", "")
                    .queryParam("CRS", "EPSG:2056")
                    .queryParam("BBOX", bbox)
                    .queryParam("WIDTH", IMAGE_SIZE)
                    .queryParam("HEIGHT", IMAGE_SIZE)
                    .queryParam("I", CENTER_PIXEL)
                    .queryParam("J", CENTER_PIXEL)
                    .queryParam("X", CENTER_PIXEL)
                    .queryParam("Y", CENTER_PIXEL)
                    .queryParam("INFO_FORMAT", "text/xml")
                    .queryParam("WITH_GEOMETRY", true)
                    .queryParam("WITH_MAPTIP", false)
                    .queryParam("FEATURE_COUNT", 20)
                    .queryParam("FI_LINE_TOLERANCE", 8)
                    .queryParam("FI_POINT_TOLERANCE", 16)
                    .queryParam("FI_POLYGON_TOLERANCE", 4)
                    .queryParam("RADIUS", 16)
                    .build(true)
                    .toUri();

            log.info("Geothermal GetFeatureInfo request: {}", uri);

            ResponseEntity<String> response = geothermalClient.get().uri(uri).retrieve().toEntity(String.class);

            if (response.getStatusCode().isError() || response.getBody() == null || response.getBody().isBlank()) {
                return new ProcessingResult(Status.ERROR, List.of(),
                        "Erdwärmesonden-Abfrage lieferte keine gültige Antwort.");
            }

            ParsedFeature parsed = parseFeatureInfo(response.getBody());
            if (parsed == null || parsed.resultText() == null || parsed.resultText().isBlank()) {
                return new ProcessingResult(Status.ERROR, List.of(),
                        "Erdwärmesonden-Antwort enthält kein Resultat.");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", "geothermal-%s-%s".formatted(DECIMAL_FORMAT.format(coord.get(0)),
                    DECIMAL_FORMAT.format(coord.get(1))));
            payload.put("label", "Geothermal probe feasibility");
            payload.put("coord", coord);
            payload.put("crs", "EPSG:2056");
            payload.put("result", parsed.resultText());
            if (parsed.linkInfo() != null) {
                Optional.ofNullable(parsed.linkInfo().href()).filter(s -> !s.isBlank()).ifPresent(url -> {
                    payload.put("pdfUrl", url);
                    payload.put("pdfLabel", parsed.linkInfo().displayLabel());
                });
            }

            Map<String, Object> clientAction = Map.of("type", "setView",
                    "payload", Map.of("center", coord, "zoom", 17, "crs", "EPSG:2056"));

            String message = parsed.resultText();
            Optional<String> linkOpt = Optional.ofNullable(parsed.linkInfo()).flatMap(info -> Optional.ofNullable(info.href()));
            if (linkOpt.isPresent()) {
                String anchor = "<a href=\"%s\" target=\"_blank\" rel=\"noreferrer\">Resultat</a>"
                        .formatted(linkOpt.get());
                message = message == null || message.isBlank() ? anchor : (message + " " + anchor);
            }
            return new ProcessingResult(Status.SUCCESS, McpResponseItem.toMapList(
                    List.of(new McpResponseItem("geothermal", payload, List.of(), clientAction))), message);
        } catch (RestClientResponseException e) {
            log.warn("Geothermal GetFeatureInfo failed with status {}", e.getStatusCode(), e);
            return new ProcessingResult(Status.ERROR, List.of(),
                    "Erdwärmesonden-Dienst antwortete nicht erfolgreich (HTTP %d).".formatted(e.getStatusCode().value()));
        } catch (RestClientException e) {
            log.error("Geothermal GetFeatureInfo call failed", e);
            return new ProcessingResult(Status.ERROR, List.of(),
                    "Erdwärmesonden-Dienst konnte nicht erreicht werden.");
        } catch (Exception e) {
            log.error("Error processing geothermal response", e);
            return new ProcessingResult(Status.ERROR, List.of(),
                    "Erdwärmesonden-Antwort konnte nicht verarbeitet werden.");
        }
    }

    @McpTool(name = "processing.getCadastralPlanByGeometry", description = "Erzeugt einen Grundbuchplan-PDF aus einer GeoJSON-Geometrie via Landregister-Print-Service")
    public ProcessingResult getCadastralPlanByGeometry(
            @McpToolParam(description = "GeoJSON-Geometrie des Grundstücks", required = true)
            @McpToolArgSchema("{ 'geometry': 'GeoJSON' }")
            Map<String, Object> args) {
        GeometryInput geometry = resolveGeometry(args);
        if (geometry.geometry().isEmpty() || geometry.extent().isEmpty()) {
            return new ProcessingResult(Status.ERROR, List.of(),
                    "Keine Geometrie/Extent für den Grundbuchplan übergeben.");
        }

        PrintRequest printRequest = buildPrintRequest(geometry.extent());
        if (printRequest == null) {
            return new ProcessingResult(Status.ERROR, List.of(), "Extent konnte nicht berechnet werden.");
        }

        String egrid = extractEgrid(args);

        try {
            MultiValueMap<String, String> body = buildPrintFormBody(printRequest);
            ResponseEntity<byte[]> response = landregPrintClient.post().uri(landregPrintProperties.getService())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);

            if (response.getStatusCode().isError() || response.getBody() == null || response.getBody().length == 0) {
                return new ProcessingResult(Status.ERROR, List.of(),
                        "Landregister-Print-Service antwortete nicht erfolgreich.");
            }

            PrintFileStorage.StoredPdf stored = printFileStorage.storePdf(response.getBody());
            List<Double> center = geometry.centroid().isEmpty() ? deriveCenter(printRequest.extent()) : geometry.centroid();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", Optional.ofNullable(egrid).orElse("cadastral-plan"));
            Optional.ofNullable(egrid).ifPresent(idVal -> {
                payload.put("egrid", idVal);
                payload.put("label", "Grundbuchplan " + idVal);
            });
            payload.putIfAbsent("label", "Grundbuchplan");
            payload.put("extent", printRequest.extent());
            payload.put("scaleDenominator", printRequest.scaleDenominator());
            payload.put("gridInterval", printRequest.gridInterval());
            payload.put("template", landregPrintProperties.getTemplate());
            payload.put("dpi", landregPrintProperties.getDpi());
            payload.put("srs", landregPrintProperties.getSrs());
            payload.put("pdfUrl", stored.url());
            payload.put("pdfSize", stored.size());
            payload.put("expiresAt", stored.expiresAt().toString());
            if (!geometry.geometry().isEmpty()) {
                payload.put("geometry", geometry.geometry());
            }
            if (!center.isEmpty()) {
                payload.put("coord", center);
                payload.put("crs", landregPrintProperties.getSrs());
            }

            Map<String, Object> clientAction = center.isEmpty() ? Map.of()
                    : Map.of("type", "setView",
                            "payload", Map.of("center", center, "zoom", 17, "crs", landregPrintProperties.getSrs()));

            String message = "Grundbuchplan erstellt. PDF als Data-URL im Payload verfügbar.";
            return new ProcessingResult(Status.SUCCESS,
                    McpResponseItem.toMapList(
                            List.of(new McpResponseItem("cadastral-plan", payload, List.of(), clientAction))),
                    message);
        } catch (RestClientResponseException e) {
            log.warn("Landregister-Print-Service antwortete mit Status {}", e.getStatusCode(), e);
            return new ProcessingResult(Status.ERROR, List.of(),
                    "Landregister-Print-Service antwortete nicht erfolgreich (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            log.error("Landregister-Print-Service nicht erreichbar", e);
            return new ProcessingResult(Status.ERROR, List.of(), "Landregister-Print-Service konnte nicht erreicht werden.");
        } catch (Exception e) {
            log.error("Fehler beim Erzeugen des Grundbuchplans", e);
            return new ProcessingResult(Status.ERROR, List.of(), "Grundbuchplan konnte nicht erzeugt werden.");
        }
    }

    ParsedFeature parseFeatureInfo(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        String resultText = extractAttribute(doc, "resultat").map(HtmlUtils::htmlUnescape).orElse(null);
        String pdfValue = extractAttribute(doc, "pdf_link").map(HtmlUtils::htmlUnescape).orElse(null);
        LinkInfo link = pdfValue != null ? parseLink(pdfValue) : null;

        return new ParsedFeature(resultText, link);
    }

    private Optional<String> extractAttribute(Document doc, String attributeName) {
        NodeList attributes = doc.getElementsByTagName("Attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            if (!(attributes.item(i) instanceof Element el)) {
                continue;
            }
            String name = el.getAttribute("name");
            if (name == null || name.isBlank()) {
                name = el.getAttribute("attrname");
            }
            if (attributeName.equalsIgnoreCase(name)) {
                String value = el.getAttribute("value");
                if (value == null || value.isBlank()) {
                    value = el.getTextContent();
                }
                return Optional.ofNullable(value).map(String::trim).filter(s -> !s.isBlank());
            }
        }
        return Optional.empty();
    }

    private LinkInfo parseLink(String htmlAnchor) {
        String href = null;
        String label = null;
        if (htmlAnchor != null) {
            Matcher hrefMatcher = HREF_PATTERN.matcher(htmlAnchor);
            if (hrefMatcher.find()) {
                href = hrefMatcher.group(1);
            }
            Matcher textMatcher = LINK_TEXT_PATTERN.matcher(htmlAnchor);
            if (textMatcher.find()) {
                label = textMatcher.group(1);
            }
        }
        return new LinkInfo(href, label);
    }

    private String buildBbox(double x, double y, double resolution) {
        double halfSpan = (IMAGE_SIZE / 2.0) * resolution;
        double minX = x - halfSpan;
        double minY = y - halfSpan;
        double maxX = x + halfSpan;
        double maxY = y + halfSpan;
        return "%s,%s,%s,%s".formatted(DECIMAL_FORMAT.format(minX), DECIMAL_FORMAT.format(minY),
                DECIMAL_FORMAT.format(maxX), DECIMAL_FORMAT.format(maxY));
    }

    private static DecimalFormat decimalFormatter() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.####", symbols);
        df.setGroupingUsed(false);
        return df;
    }

    PrintRequest buildPrintRequest(List<Double> extent) {
        if (extent == null || extent.size() < 4) {
            return null;
        }
        double minX = extent.get(0);
        double minY = extent.get(1);
        double maxX = extent.get(2);
        double maxY = extent.get(3);
        double width = maxX - minX;
        double height = maxY - minY;
        if (width <= 0 || height <= 0) {
            return null;
        }

        var allowedScales = new java.util.TreeSet<>(landregPrintProperties.getAllowedScales());
        var allowedGridIntervals = new java.util.TreeSet<>(landregPrintProperties.getAllowedGridIntervals());
        if (allowedScales.isEmpty() || allowedGridIntervals.isEmpty()) {
            return null;
        }

        double scaleW = landregPrintProperties.getLayoutWidth() / width;
        double scaleH = landregPrintProperties.getLayoutHeight() / height;

        boolean scaleFitW = scaleW < scaleH;
        double scaleDenominator = 1.0 / Math.min(scaleW, scaleH);

        Integer fitScaleDen = allowedScales.higher((int) Math.ceil(scaleDenominator));
        if (fitScaleDen == null) {
            fitScaleDen = allowedScales.last();
        }

        double factor = fitScaleDen / scaleDenominator;
        double newWidth;
        double newHeight;
        if (scaleFitW) {
            newWidth = factor * width;
            newHeight = newWidth * landregPrintProperties.getLayoutHeight() / landregPrintProperties.getLayoutWidth();
        } else {
            newHeight = factor * height;
            newWidth = newHeight * landregPrintProperties.getLayoutWidth() / landregPrintProperties.getLayoutHeight();
        }

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;

        double newMinX = centerX - 0.5 * newWidth;
        double newMinY = centerY - 0.5 * newHeight;
        double newMaxX = centerX + 0.5 * newWidth;
        double newMaxY = centerY + 0.5 * newHeight;

        int divisor = Math.max(1, landregPrintProperties.getGridIntervalTargetDivisor());
        Integer gridInterval = allowedGridIntervals.ceiling((int) Math.ceil(newWidth / divisor));
        if (gridInterval == null) {
            gridInterval = allowedGridIntervals.last();
        }

        return new PrintRequest(List.of(newMinX, newMinY, newMaxX, newMaxY), fitScaleDen, gridInterval);
    }

    private MultiValueMap<String, String> buildPrintFormBody(PrintRequest request) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("TEMPLATE", landregPrintProperties.getTemplate());
        body.add("scale", String.valueOf(request.scaleDenominator()));
        body.add("rotation", "0");
        body.add("extent", formatExtent(request.extent()));
        body.add("SRS", landregPrintProperties.getSrs());
        body.add("GRID_INTERVAL_X", String.valueOf(request.gridInterval()));
        body.add("GRID_INTERVAL_Y", String.valueOf(request.gridInterval()));
        body.add("DPI", String.valueOf(landregPrintProperties.getDpi()));
        return body;
    }

    private GeometryInput resolveGeometry(Map<String, Object> args) {
        Map<String, Object> payload = Map.of();
        if (args.get("selection") instanceof Map<?, ?> selectionMap) {
            Map<String, Object> normalizedSelection = new LinkedHashMap<>();
            selectionMap.forEach((k, v) -> normalizedSelection.put(String.valueOf(k), v));
            payload = McpResponseItem.payload(normalizedSelection);
        } else if (args != null) {
            payload = McpResponseItem.payload(args);
        }

        Map<String, Object> geometry = McpResponseItem.normalizeGeometry(payload.get("geometry"));
        List<Double> extent = McpResponseItem.extent(payload);
        List<Double> centroid = McpResponseItem.centroid(payload);
        return new GeometryInput(extent, geometry, centroid);
    }

    private String extractEgrid(Map<String, Object> args) {
        if (args == null) {
            return null;
        }
        Object direct = args.getOrDefault("egrid", args.get("id"));
        if (direct instanceof String s && !s.isBlank()) {
            return s;
        }
        if (args.get("selection") instanceof Map<?, ?> selectionMap) {
            Object candidate = selectionMap.get("egrid");
            if (candidate instanceof String s && !s.isBlank()) {
                return s;
            }
            candidate = selectionMap.get("id");
            if (candidate instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private String formatExtent(List<Double> extent) {
        if (extent == null || extent.size() < 4) {
            return "";
        }
        return "%s,%s,%s,%s".formatted(DECIMAL_FORMAT.format(extent.get(0)), DECIMAL_FORMAT.format(extent.get(1)),
                DECIMAL_FORMAT.format(extent.get(2)), DECIMAL_FORMAT.format(extent.get(3)));
    }

    private List<Double> deriveCenter(List<Double> extent) {
        if (extent == null || extent.size() < 4) {
            return List.of();
        }
        double centerX = (extent.get(0) + extent.get(2)) / 2.0;
        double centerY = (extent.get(1) + extent.get(3)) / 2.0;
        return List.of(centerX, centerY);
    }

    record PrintRequest(List<Double> extent, int scaleDenominator, int gridInterval) {
    }

    record GeometryInput(List<Double> extent, Map<String, Object> geometry, List<Double> centroid) {
    }

    private Double asDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }
        return fallback;
    }
}
