package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ch.so.agi.ask.mcp.McpToolArgSchema;

import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

@Component
public class OerebTools {

    private static final Logger log = LoggerFactory.getLogger(OerebTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/oereb/getegrid/xml/";
    private static final DecimalFormat DECIMAL_FORMAT = decimalFormatter();

    private final RestClient restClient;

    public OerebTools(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    public record OerebResult(String status, List<Map<String, Object>> items, String message) implements ToolResult {
    }

    @McpTool(name = "oereb.egridByXY", description = "Ermittelt ÖREB-EGRID(s) und Geometrie anhand von LV95-Koordinaten")
    public OerebResult getOerebEgridByXY(
            @McpToolParam(description = "Coordinate input, expecting keys 'x' and 'y' or 'coord' array", required = true)
            @McpToolArgSchema("{ 'x': 'number - LV95 east', 'y': 'number - LV95 north', 'coord': '[east, north]' }")
            Map<String, Object> args) {
        Double x = asDouble(args.get("x"), null);
        Double y = asDouble(args.get("y"), null);
        List<Double> coord = args.containsKey("coord") && args.get("coord") instanceof List<?> raw
                ? raw.stream().map(o -> asDouble(o, null)).filter(Objects::nonNull).toList()
                : (x != null && y != null ? List.of(x, y) : List.of());

        if (coord.size() < 2 || coord.stream().anyMatch(Objects::isNull)) {
            return new OerebResult("error", List.of(), "Ungültige Koordinate übergeben.");
        }

        String enParam = DECIMAL_FORMAT.format(coord.get(0)) + "," + DECIMAL_FORMAT.format(coord.get(1));

        try {
            ResponseEntity<String> response = restClient.get().uri(uriBuilder -> uriBuilder
                    .queryParam("GEOMETRY", "true")
                    .queryParam("EN", enParam)
                    .build()).retrieve().toEntity(String.class);

            if (response.getStatusCode().value() == 204 || response.getBody() == null || response.getBody().isBlank()) {
                return new OerebResult("error", List.of(), "Kein Grundstück gefunden.");
            }

            List<Map<String, Object>> items = parseResponse(response.getBody(), coord);
            String message = items.isEmpty() ? "Kein Grundstück gefunden."
                    : (items.size() > 1 ? "Mehrere Grundstücke gefunden." : "Grundstück gefunden.");
            String status = items.isEmpty() ? "error" : "ok";
            return new OerebResult(status, items, message);
        } catch (RestClientResponseException e) {
            log.warn("ÖREB GetEGRID call failed with status {}", e.getStatusCode(), e);
            return new OerebResult("error", List.of(),
                    "ÖREB-Antwort schlug fehl (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            log.error("ÖREB-GetEGRID-Aufruf fehlgeschlagen", e);
            return new OerebResult("error", List.of(), "ÖREB-Dienst konnte nicht erreicht werden.");
        } catch (Exception e) {
            log.error("Fehler beim Verarbeiten der ÖREB-Antwort", e);
            return new OerebResult("error", List.of(), "Die Antwort des ÖREB-Dienstes konnte nicht verarbeitet werden.");
        }
    }

    @McpTool(name = "oereb.extractById", description = "Erzeugt ÖREB-Auszug-URLs für ein EGRID")
    public OerebResult getOerebExtractById(
            @McpToolParam(description = "Must include 'egrid' or 'selection' with an id")
            @McpToolArgSchema("{ 'egrid': 'string id', 'selection': { 'egrid'|'id': 'string', 'coord': [east, north] } }")
            Map<String, Object> args) {
        String egrid = extractEgrid(args);
        if (egrid == null || egrid.isBlank()) {
            return new OerebResult("error", List.of(), "Kein EGRID übergeben.");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", egrid);
        item.put("egrid", egrid);
        item.put("label", "ÖREB-Auszug für %s".formatted(egrid));
        item.put("pdfUrl", "https://geo.so.ch/api/oereb/extract/pdf/?EGRID=%s".formatted(egrid));
        item.put("mapUrl", "https://geo.so.ch/map/?oereb_egrid=%s".formatted(egrid));

        if (args.get("selection") instanceof Map<?, ?> selectionMap) {
            Object coord = selectionMap.get("coord");
            if (coord != null) {
                item.put("coord", coord);
            }
            Object geometry = selectionMap.get("geometry");
            if (geometry != null) {
                item.put("geometry", geometry);
            }
        }

        String message = "ÖREB-Auszug erstellt.\nPDF: %s\nFachanwendung: %s".formatted(item.get("pdfUrl"),
                item.get("mapUrl"));
        return new OerebResult("ok", List.of(item), message);
    }

    private List<Map<String, Object>> parseResponse(String xml, List<Double> fallbackCoord) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        Element root = doc.getDocumentElement();
        NodeList children = root.getChildNodes();

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> current = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = localName(node);
            switch (name) {
            case "egrid" -> {
                current = new LinkedHashMap<>();
                current.put("id", text(node));
                current.put("egrid", text(node));
                current.put("crs", "EPSG:2056");
                items.add(current);
            }
            case "number" -> Optional.ofNullable(current).ifPresent(map -> map.put("number", text(node)));
            case "identDN" -> Optional.ofNullable(current).ifPresent(map -> map.put("identDN", text(node)));
            case "type" -> Optional.ofNullable(current)
                    .ifPresent(map -> map.put("propertyType", extractPropertyType((Element) node)));
            case "limit" -> Optional.ofNullable(current).ifPresent(map -> {
                GeometryResult geometry = extractGeometry((Element) node);
                if (geometry.geoJson() != null) {
                    map.put("geometry", geometry.geoJson());
                }
                if (!geometry.centroid().isEmpty()) {
                    map.put("coord", geometry.centroid());
                } else if (!fallbackCoord.isEmpty()) {
                    map.put("coord", fallbackCoord);
                }
            });
            default -> {
            }
            }
        }

        for (Map<String, Object> item : items) {
            String egrid = (String) item.getOrDefault("egrid", item.get("id"));
            String propertyType = Optional.ofNullable((String) item.get("propertyType")).orElse("Grundstück");
            String number = Optional.ofNullable((String) item.get("number")).orElse("");
            String label = number.isBlank() ? "%s – %s".formatted(egrid, propertyType)
                    : "%s – %s (%s)".formatted(egrid, propertyType, number);
            item.put("label", label);
            item.putIfAbsent("coord", fallbackCoord);
        }

        return items;
    }

    private String extractPropertyType(Element typeElement) {
        List<Element> textNodes = findChildren(typeElement, "Text");
        for (Element textNode : textNodes) {
            List<Element> localised = findChildren(textNode, "LocalisedText");
            for (Element loc : localised) {
                List<Element> langNodes = findChildren(loc, "Language");
                if (!langNodes.isEmpty() && "de".equalsIgnoreCase(text(langNodes.get(0)))) {
                    List<Element> valueNodes = findChildren(loc, "Text");
                    if (!valueNodes.isEmpty()) {
                        return text(valueNodes.get(0));
                    }
                }
            }
        }

        // Fallback: erster Text-Knoten
        for (Element textNode : textNodes) {
            List<Element> valueNodes = findChildren(textNode, "Text");
            if (!valueNodes.isEmpty()) {
                return text(valueNodes.get(0));
            }
        }
        return "Grundstück";
    }

    private GeometryResult extractGeometry(Element limitElement) {
        List<List<List<List<Double>>>> polygons = new ArrayList<>();
        List<Element> surfaces = findChildren(limitElement, "surface");
        if (surfaces.isEmpty() && localName(limitElement).equals("surface")) {
            surfaces = List.of(limitElement);
        }

        for (Element surface : surfaces) {
            List<List<List<Double>>> rings = new ArrayList<>();
            for (Element boundary : findChildren(surface, "exterior")) {
                List<List<Double>> ring = extractRing(boundary);
                if (!ring.isEmpty()) {
                    rings.add(ring);
                }
            }
            for (Element boundary : findChildren(surface, "interior")) {
                List<List<Double>> ring = extractRing(boundary);
                if (!ring.isEmpty()) {
                    rings.add(ring);
                }
            }
            if (!rings.isEmpty()) {
                polygons.add(rings);
            }
        }

        if (polygons.isEmpty()) {
            return new GeometryResult(null, List.of());
        }

        Map<String, Object> geoJson;
        if (polygons.size() == 1) {
            geoJson = Map.of("type", "Polygon", "coordinates", polygons.get(0));
        } else {
            geoJson = Map.of("type", "MultiPolygon", "coordinates", polygons);
        }

        List<Double> centroid = computeCentroid(polygons);
        return new GeometryResult(geoJson, centroid);
    }

    private List<List<Double>> extractRing(Element boundaryElement) {
        List<List<Double>> coords = new ArrayList<>();
        for (Element polyline : findChildren(boundaryElement, "polyline")) {
            List<Element> coordNodes = findChildren(polyline, "coord");
            for (Element coordNode : coordNodes) {
                List<Element> c1Nodes = findChildren(coordNode, "c1");
                List<Element> c2Nodes = findChildren(coordNode, "c2");
                if (c1Nodes.isEmpty() || c2Nodes.isEmpty()) {
                    continue;
                }
                Double c1 = asDouble(text(c1Nodes.get(0)), null);
                Double c2 = asDouble(text(c2Nodes.get(0)), null);
                if (c1 != null && c2 != null) {
                    coords.add(List.of(c1, c2));
                }
            }
        }

        if (!coords.isEmpty()) {
            List<Double> first = coords.getFirst();
            List<Double> last = coords.getLast();
            if (first.size() >= 2 && last.size() >= 2
                    && (!Objects.equals(first.get(0), last.get(0)) || !Objects.equals(first.get(1), last.get(1)))) {
                coords.add(List.of(first.get(0), first.get(1)));
            }
        }

        return coords;
    }

    private List<Double> computeCentroid(List<List<List<List<Double>>>> polygons) {
        double sumX = 0d;
        double sumY = 0d;
        int count = 0;

        for (List<List<List<Double>>> polygon : polygons) {
            if (polygon.isEmpty()) {
                continue;
            }
            List<List<Double>> exterior = polygon.get(0);
            for (List<Double> coord : exterior) {
                if (coord.size() < 2) {
                    continue;
                }
                sumX += coord.get(0);
                sumY += coord.get(1);
                count++;
            }
        }

        if (count == 0) {
            return List.of();
        }
        return List.of(sumX / count, sumY / count);
    }

    private List<Element> findChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName(node).equals(localName)) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private String localName(Node node) {
        String local = node.getLocalName();
        if (local != null) {
            return local;
        }
        String name = node.getNodeName();
        int idx = name.indexOf(':');
        return idx >= 0 ? name.substring(idx + 1) : name;
    }

    private String text(Node node) {
        return node == null ? "" : node.getTextContent().trim();
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

    private String extractEgrid(Map<String, Object> args) {
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
            if (candidate instanceof String s2 && !s2.isBlank()) {
                return s2;
            }
        }
        return null;
    }

    private record GeometryResult(Map<String, Object> geoJson, List<Double> centroid) {
    }
}
