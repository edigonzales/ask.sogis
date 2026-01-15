package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.so.agi.ask.mcp.McpToolArgSchema;
import ch.so.agi.ask.mcp.ToolResult.Status;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GeolocationTools {

    private static final Logger log = LoggerFactory.getLogger(GeolocationTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/search/v2/";
    private static final String FILTER_VALUE = "ch.so.agi.av.gebaeudeadressen.gebaeudeeingaenge";
    private static final int DEFAULT_LIMIT = 25;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeolocationTools(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.objectMapper = objectMapper;
    }

    public record GeolocationResult(
            Status status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    @McpTool(
            name = "geolocation.geocode",
            description = "Geocoder for Swiss Solothurn addresses using the geo.so.ch API."
    )
    public GeolocationResult geocodeAddress(
            @McpToolParam(description = "Query string that represents an address", required = true)
            @McpToolArgSchema("{ 'q': 'string - full address query' }")
            Map<String, Object> args) {
        String q = (String) args.getOrDefault("q", "");
        log.info("MCP geolocation.geocode called with q={}", q);

        if (q == null || q.isBlank()) {
            return new GeolocationResult(Status.ERROR, List.of(), "Parameter 'q' darf nicht leer sein.");
        }

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("filter", FILTER_VALUE)
                            .queryParam("limit", DEFAULT_LIMIT)
                            .queryParam("searchtext", q)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            List<McpResponseItem> mappedResults = mapResults(root.path("results"));
            List<McpResponseItem> exactMatches = filterExactMatches(q, mappedResults);
            List<McpResponseItem> items = exactMatches.isEmpty() ? mappedResults : exactMatches;

            String message = items.isEmpty() ? "Keine Treffer gefunden."
                    : String.format("%d Treffer gefunden.", items.size());
            Status status = items.isEmpty() ? Status.ERROR
                    : (items.size() > 1 ? Status.NEEDS_USER_CHOICE : Status.SUCCESS);

            return new GeolocationResult(status, McpResponseItem.toMapList(items), message);
        } catch (RestClientResponseException e) {
            log.warn("Geocoder call failed with status {}", e.getStatusCode(), e);
            return new GeolocationResult(
                    Status.ERROR,
                    List.of(),
                    "Geocoder-Antwort schlug fehl (HTTP " + e.getStatusCode().value() + ")."
            );
        } catch (RestClientException e) {
            log.error("Geocoder-Aufruf fehlgeschlagen", e);
            return new GeolocationResult(Status.ERROR, List.of(), "Geocoder konnte nicht erreicht werden.");
        } catch (IOException e) {
            log.error("Fehler beim Lesen der Geocoder-Antwort", e);
            return new GeolocationResult(Status.ERROR, List.of(),
                    "Antwort des Geocoders konnte nicht verarbeitet werden.");
        }
    }

    List<McpResponseItem> mapResults(JsonNode resultsNode) {
        if (resultsNode == null || !resultsNode.isArray()) {
            return List.of();
        }

        List<McpResponseItem> items = new ArrayList<>();
        resultsNode.forEach(resultNode -> {
            JsonNode featureNode = resultNode.path("feature");
            createItemFromFeature(featureNode).ifPresent(items::add);
        });
        return items;
    }

    List<McpResponseItem> filterExactMatches(String query, List<McpResponseItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeStreetAndNumber(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        return items.stream()
                .filter(item -> {
                    String normalizedLabel = normalizeStreetAndNumber(McpResponseItem.label(item.toMap()));
                    return !normalizedLabel.isBlank() && normalizedLabel.equals(normalizedQuery);
                })
                .collect(Collectors.toList());
    }

    private Optional<McpResponseItem> createItemFromFeature(JsonNode featureNode) {
        if (featureNode == null || featureNode.isMissingNode()) {
            return Optional.empty();
        }

        String id = featureNode.path("feature_id").asText(null);
        String srid = featureNode.path("srid").asText(null);
        String label = sanitizeLabel(featureNode.path("display").asText(""));
        JsonNode bboxNode = featureNode.path("bbox");

        if (id == null || srid == null || label.isBlank() || !bboxNode.isArray() || bboxNode.isEmpty()) {
            return Optional.empty();
        }

        List<Double> bboxValues = new ArrayList<>();
        bboxNode.forEach(coord -> bboxValues.add(coord.asDouble()));
        List<Double> centroid = computeCentroidFromExtent(bboxValues);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("label", label);
        payload.put("coord", centroid.isEmpty() ? bboxValues : centroid);
        payload.put("centroid", centroid);
        payload.put("extent", bboxValues);
        payload.put("crs", srid);

        Map<String, Object> clientAction = Map.of(
                "type", "setView",
                "payload", Map.of("center", centroid.isEmpty() ? bboxValues : centroid, "zoom", 17, "crs", srid));
        return Optional.of(new McpResponseItem("geolocation", payload, List.of(), clientAction));
    }

    private List<Double> computeCentroidFromExtent(List<Double> extent) {
        if (extent == null || (extent.size() != 4 && extent.size() != 2)) {
            return List.of();
        }
        if (extent.size() == 2) {
            return extent;
        }
        Double minX = extent.get(0);
        Double minY = extent.get(1);
        Double maxX = extent.get(2);
        Double maxY = extent.get(3);
        if (minX == null || minY == null || maxX == null || maxY == null) {
            return List.of();
        }
        return List.of((minX + maxX) / 2d, (minY + maxY) / 2d);
    }

    private String sanitizeLabel(String display) {
        if (display == null) {
            return "";
        }
        String label = display.replace("(Adresse)", "");
        return label.replaceAll("\\s+", " ").trim();
    }

    private String normalizeStreetAndNumber(String input) {
        if (input == null) {
            return "";
        }

        String base = input.split(",", 2)[0];
        base = base.replaceAll("\\s+", " ").trim();
        return base.toLowerCase();
    }
}
