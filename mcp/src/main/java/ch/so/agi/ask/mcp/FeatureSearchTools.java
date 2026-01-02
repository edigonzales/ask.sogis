package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ch.so.agi.ask.mcp.ToolResult.Status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unterstützungs-Tools für Abfragen auf GeoJSON-Feature-Services. Liefert aktuell
 * die EGRID-Suche anhand sprechender Grundstücksnummer und Gemeindename, damit
 * nachgelagerte ÖREB-Funktionen den passenden EGRID erhalten.
 */
@Component
public class FeatureSearchTools {

    private static final Logger log = LoggerFactory.getLogger(FeatureSearchTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/data/v1/ch.so.agi.av.grundstuecke.rechtskraeftig/";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public FeatureSearchTools(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Result-Wrapper mit standardisiertem Status/Items/Message-Schema für MCP-Tools.
     */
    public record FeatureSearchResult(Status status, List<Map<String, Object>> items, String message)
            implements ToolResult {
    }

    @McpTool(
            name = "featureSearch.getEgridByNumberAndMunicipality",
            description = """
                    Ermittelt das EGRID eines Grundstücks anhand der sprechenden Grundstücksnummer
                    (z. B. «168») und des Gemeindenamens über den geo.so.ch-Feature-Service.
                    Mehrere Treffer werden als Auswahloptionen zurückgegeben (Status NEEDS_USER_CHOICE).
                    """
    )
    public FeatureSearchResult getEgridByNumberAndMunicipality(
            @McpToolParam(
                    description = """
                            Erwartet die sprechende Grundstücksnummer («nummer») und den Gemeindenamen («municipality»).
                            Beide Werte sollten so übergeben werden, wie sie den Nutzern bekannt sind, da die Abfrage
                            case-sensitiv ist. Die Methode liefert den oder die passenden EGRID-Werte als Items zurück.
                            """,
                    required = true
            )
            @McpToolArgSchema("{ 'number': 'string - sprechende Grundstücksnummer', 'municipality': 'string - Gemeindename' }")
            Map<String, Object> args) {

        String number = asTrimmedString(args.get("number"));
        String municipality = asTrimmedString(args.get("municipality"));

        if (number.isBlank() || municipality.isBlank()) {
            return new FeatureSearchResult(Status.ERROR, List.of(),
                    "Sowohl Grundstücksnummer ('number') als auch Gemeindename ('municipality') sind erforderlich.");
        }

        log.info("MCP featureSearch.getEgridByNumberAndMunicipality called with number={} municipality={}", number,
                municipality);

        try {
            String filter = buildFilter(number, municipality);
            String body = restClient.get().uri(uriBuilder -> uriBuilder.queryParam("filter", filter).build()).retrieve()
                    .body(String.class);

            List<McpResponseItem> items = mapFeatures(body);
            if (items.isEmpty()) {
                return new FeatureSearchResult(Status.ERROR, List.of(),
                        "Kein Grundstück %s in %s gefunden.".formatted(number, municipality));
            }

            Status status = items.size() > 1 ? Status.NEEDS_USER_CHOICE : Status.SUCCESS;
            String message = status == Status.SUCCESS ? "EGRID ermittelt."
                    : "Mehrere Grundstücke gefunden. Bitte Auswahl treffen.";
            return new FeatureSearchResult(status, McpResponseItem.toMapList(items), message);
        } catch (RestClientResponseException e) {
            log.warn("Feature search call failed with status {}", e.getStatusCode(), e);
            return new FeatureSearchResult(Status.ERROR, List.of(),
                    "Feature-Service antwortete nicht erfolgreich (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            log.error("Feature search request failed", e);
            return new FeatureSearchResult(Status.ERROR, List.of(), "Feature-Service konnte nicht erreicht werden.");
        } catch (IOException e) {
            log.error("Failed to parse feature search response", e);
            return new FeatureSearchResult(Status.ERROR, List.of(),
                    "Antwort des Feature-Service konnte nicht verarbeitet werden.");
        }
    }

    List<McpResponseItem> mapFeatures(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode features = root.path("features");
        if (features == null || !features.isArray()) {
            return List.of();
        }

        List<McpResponseItem> items = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            String egrid = properties.path("egrid").asText(null);
            if (egrid == null || egrid.isBlank()) {
                continue;
            }

            String number = properties.path("nummer").asText("");
            String municipality = properties.path("gemeinde").asText("");
            String landRegister = properties.path("grundbuch").asText("");
            String propertyType = properties.path("art_txt").asText("");

            Map<String, Object> geometry = null;
            if (feature.hasNonNull("geometry")) {
                geometry = McpResponseItem.normalizeGeometry(objectMapper.convertValue(feature.get("geometry"),
                        new TypeReference<Map<String, Object>>() {
                        }));
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", egrid);
            payload.put("egrid", egrid);
            payload.put("label", buildLabel(number, municipality, egrid, propertyType, landRegister));
            payload.put("number", number);
            payload.put("municipality", municipality);
            payload.put("landRegister", landRegister);
            payload.put("propertyType", propertyType);
            List<Double> centroid = geometry == null ? List.of() : McpResponseItem.deriveCentroid(geometry);
            List<Double> extent = geometry == null ? List.of() : McpResponseItem.deriveExtent(geometry);

            if (geometry != null && !geometry.isEmpty()) {
                payload.put("geometry", geometry);
            }
            if (!centroid.isEmpty()) {
                payload.put("coord", centroid);
                payload.put("centroid", centroid);
                payload.put("crs", "EPSG:2056");
            }
            if (!extent.isEmpty()) {
                payload.put("extent", extent);
            }

            Map<String, Object> clientAction = new LinkedHashMap<>();
            clientAction.put("type", "setView");
            clientAction.put("payload", Map.of("center", payload.getOrDefault("coord", centroid), "zoom", 17,
                    "crs", payload.getOrDefault("crs", "EPSG:2056")));

            items.add(new McpResponseItem("oereb-parcel", payload, List.of(), clientAction));
        }

        return items;
    }

    private String buildFilter(String number, String municipality) {
        String safeNumber = number.replace("\"", "\\\"");
        String safeMunicipality = municipality.replace("\"", "\\\"");
        return "[[\"nummer\",\"=\",\"%s\"],\"and\",[\"gemeinde\",\"=\",\"%s\"]]".formatted(safeNumber, safeMunicipality);
    }

    private String buildLabel(String number, String municipality, String egrid, String propertyType,
            String landRegister) {
        StringBuilder label = new StringBuilder("Grundstück ");
        if (!number.isBlank()) {
            label.append(number).append(" ");
        }
        if (!municipality.isBlank()) {
            label.append(municipality);
        }
        if (!propertyType.isBlank()) {
            label.append(" (").append(propertyType).append(")");
        }
        if (!landRegister.isBlank()) {
            label.append(" – Grundbuch ").append(landRegister);
        }
        label.append(" – ").append(egrid);
        return label.toString().trim();
    }

    private String asTrimmedString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return value.toString().trim();
    }
}
