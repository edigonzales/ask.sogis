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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Component
public class GeolocationTools {

    private static final Logger log = LoggerFactory.getLogger(GeolocationTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/search/v2/";
    private static final String ADDRESS_FILTER = "ch.so.agi.av.gebaeudeadressen.gebaeudeeingaenge";
    private static final String MUNICIPALITY_FILTER = "ch.so.agi.gemeindegrenzen";
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
            name = "geolocation.geocode.address",
            description = "Geocoder for Swiss Solothurn addresses using the geo.so.ch API."
    )
    public GeolocationResult geocodeAddress(
            @McpToolParam(description = "Query string that represents an address", required = true)
            @McpToolArgSchema("{ 'q': 'string - full address query' }")
            Map<String, Object> args) {
        String q = (String) args.getOrDefault("q", "");
        log.info("MCP geolocation.geocode.address called with q={}", q);

        if (q == null || q.isBlank()) {
            return new GeolocationResult(Status.ERROR, List.of(), "Parameter 'q' darf nicht leer sein.");
        }

        return executeGeocode(q, ADDRESS_FILTER, this::sanitizeAddressLabel, true, false,
                "Keine Treffer gefunden.", "%d Treffer gefunden.");
    }

    @McpTool(
            name = "geolocation.geocode.municipality",
            description = "Geocoder for Swiss Solothurn municipalities using the geo.so.ch API."
    )
    public GeolocationResult geocodeMunicipality(
            @McpToolParam(description = "Query string that represents a municipality", required = true)
            @McpToolArgSchema("{ 'q': 'string - municipality name' }")
            Map<String, Object> args) {
        String q = (String) args.getOrDefault("q", "");
        log.info("MCP geolocation.geocode.municipality called with q={}", q);

        if (q == null || q.isBlank()) {
            return new GeolocationResult(Status.ERROR, List.of(), "Parameter 'q' darf nicht leer sein.");
        }

        return executeGeocode(q, MUNICIPALITY_FILTER, this::sanitizeMunicipalityLabel, false, true,
                "Keine Gemeinden gefunden.", "%d Gemeinden gefunden.");
    }

    private GeolocationResult executeGeocode(String query, String filterValue, UnaryOperator<String> labelNormalizer,
            boolean allowExactMatches, boolean includeDisplayName, String emptyMessage, String messageTemplate) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("filter", filterValue)
                            .queryParam("limit", DEFAULT_LIMIT)
                            .queryParam("searchtext", query)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            List<McpResponseItem> mappedResults = mapResults(root.path("results"), labelNormalizer, includeDisplayName);
            List<McpResponseItem> items = selectItems(query, mappedResults, allowExactMatches);
            String message = items.isEmpty() ? emptyMessage : String.format(messageTemplate, items.size());
            Status status = items.isEmpty() ? Status.ERROR : (items.size() > 1 ? Status.NEEDS_USER_CHOICE : Status.SUCCESS);

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

    List<McpResponseItem> mapResults(JsonNode resultsNode, UnaryOperator<String> labelNormalizer,
            boolean includeDisplayName) {
        if (resultsNode == null || !resultsNode.isArray()) {
            return List.of();
        }

        List<McpResponseItem> items = new ArrayList<>();
        resultsNode.forEach(resultNode -> {
            JsonNode featureNode = resultNode.path("feature");
            createItemFromFeature(featureNode, labelNormalizer, includeDisplayName).ifPresent(items::add);
        });
        return items;
    }

    private List<McpResponseItem> selectItems(String query, List<McpResponseItem> items, boolean allowExactMatches) {
        if (!allowExactMatches) {
            return items;
        }
        List<McpResponseItem> exactMatches = filterExactMatches(query, items);
        return exactMatches.isEmpty() ? items : exactMatches;
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

    private Optional<McpResponseItem> createItemFromFeature(JsonNode featureNode, UnaryOperator<String> labelNormalizer,
            boolean includeDisplayName) {
        if (featureNode == null || featureNode.isMissingNode()) {
            return Optional.empty();
        }

        String id = featureNode.path("feature_id").asText(null);
        String srid = featureNode.path("srid").asText(null);
        String label = labelNormalizer.apply(featureNode.path("display").asText(""));
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
        if (includeDisplayName) {
            payload.put("displayName", label);
            payload.put("bbox", bboxValues);
        }

        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("center", centroid.isEmpty() ? bboxValues : centroid);
        actionPayload.put("zoom", 17);
        actionPayload.put("crs", srid);
        if (includeDisplayName) {
            actionPayload.put("extent", bboxValues);
        }
        Map<String, Object> clientAction = Map.of("type", "setView", "payload", actionPayload);
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

    private String sanitizeAddressLabel(String display) {
        if (display == null) {
            return "";
        }
        String label = display.replace("(Adresse)", "");
        return label.replaceAll("\\s+", " ").trim();
    }

    private String sanitizeMunicipalityLabel(String display) {
        if (display == null) {
            return "";
        }
        return display.replaceAll("\\s+", " ").trim();
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
