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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ch.so.agi.ask.mcp.ToolResult.Status;
import ch.so.agi.ask.mcp.McpToolArgSchema;

@Component
public class ProcessingTools {

    private static final Logger log = LoggerFactory.getLogger(ProcessingTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/wms";
    private static final String LAYER_NAME = "ch.so.afu.ewsabfrage.abfrage";
    private static final int IMAGE_SIZE = 101;
    private static final int CENTER_PIXEL = (IMAGE_SIZE + 1) / 2; // 51 für 101px
    private static final DecimalFormat DECIMAL_FORMAT = decimalFormatter();
    private static final Pattern HREF_PATTERN = Pattern.compile("href=['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_TEXT_PATTERN = Pattern.compile(">([^<]+)<");

    private final RestClient restClient;

    public ProcessingTools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
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
            ResponseEntity<String> response = restClient.get().uri(uriBuilder -> uriBuilder
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
                    .build()).retrieve().toEntity(String.class);

            if (response.getStatusCode().isError() || response.getBody() == null || response.getBody().isBlank()) {
                return new ProcessingResult(Status.ERROR, List.of(),
                        "Erdwärmesonden-Abfrage lieferte keine gültige Antwort.");
            }

            ParsedFeature parsed = parseFeatureInfo(response.getBody());
            if (parsed == null || parsed.resultText() == null || parsed.resultText().isBlank()) {
                return new ProcessingResult(Status.ERROR, List.of(),
                        "Erdwärmesonden-Antwort enthält kein Resultat.");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", "geothermal-%s-%s".formatted(DECIMAL_FORMAT.format(coord.get(0)),
                    DECIMAL_FORMAT.format(coord.get(1))));
            item.put("label", "Geothermal probe feasibility");
            item.put("coord", coord);
            item.put("crs", "EPSG:2056");
            item.put("result", parsed.resultText());
            if (parsed.linkInfo() != null) {
                Optional.ofNullable(parsed.linkInfo().href()).filter(s -> !s.isBlank()).ifPresent(url -> {
                    item.put("pdfUrl", url);
                    item.put("pdfLabel", parsed.linkInfo().displayLabel());
                });
            }

            String linkPart = Optional.ofNullable(parsed.linkInfo())
                    .map(info -> Optional.ofNullable(info.href()).filter(s -> !s.isBlank())
                            .map(url -> "%s: %s".formatted(info.displayLabel(), url))
                            .orElse("Kein PDF-Link verfügbar."))
                    .orElse("Kein PDF-Link verfügbar.");
            String message = parsed.resultText() + "\n" + linkPart;
            return new ProcessingResult(Status.SUCCESS, List.of(item), message);
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
