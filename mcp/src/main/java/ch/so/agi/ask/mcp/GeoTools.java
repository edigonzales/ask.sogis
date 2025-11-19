package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
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

@Component
public class GeoTools {

    private static final Logger log = LoggerFactory.getLogger(GeoTools.class);
    private static final String BASE_URL = "https://geo.so.ch/api/search/v2/";
    private static final String FILTER_VALUE = "ch.so.agi.av.gebaeudeadressen.gebaeudeeingaenge";
    private static final int DEFAULT_LIMIT = 25;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeoTools(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.objectMapper = objectMapper;
    }

    public record GeoResult(
            String status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    @McpTool(
            name = "geo.geocode",
            description = "Geocoder for Swiss Solothurn addresses using the geo.so.ch API."
    )
    public GeoResult geocode(Map<String, Object> args) {
        String q = (String) args.getOrDefault("q", "");
        log.info("MCP geo-geocode called with q={}", q);

        if (q == null || q.isBlank()) {
            return new GeoResult(
                    "error",
                    List.of(),
                    "Parameter 'q' darf nicht leer sein."
            );
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
            List<Map<String, Object>> items = mapResults(root.path("results"));

            String message = items.isEmpty()
                    ? "Keine Treffer gefunden."
                    : String.format("%d Treffer gefunden.", items.size());

            return new GeoResult(
                    "ok",
                    items,
                    message
            );
        } catch (RestClientResponseException e) {
            log.warn("Geocoder call failed with status {}", e.getStatusCode(), e);
            return new GeoResult(
                    "error",
                    List.of(),
                    "Geocoder-Antwort schlug fehl (HTTP " + e.getStatusCode().value() + ")."
            );
        } catch (RestClientException e) {
            log.error("Geocoder-Aufruf fehlgeschlagen", e);
            return new GeoResult(
                    "error",
                    List.of(),
                    "Geocoder konnte nicht erreicht werden."
            );
        } catch (IOException e) {
            log.error("Fehler beim Lesen der Geocoder-Antwort", e);
            return new GeoResult(
                    "error",
                    List.of(),
                    "Antwort des Geocoders konnte nicht verarbeitet werden."
            );
        }
    }

    List<Map<String, Object>> mapResults(JsonNode resultsNode) {
        if (resultsNode == null || !resultsNode.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> items = new ArrayList<>();
        resultsNode.forEach(resultNode -> {
            JsonNode featureNode = resultNode.path("feature");
            createItemFromFeature(featureNode).ifPresent(items::add);
        });
        return items;
    }

    private Optional<Map<String, Object>> createItemFromFeature(JsonNode featureNode) {
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

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("label", label);
        item.put("coord", bboxValues);
        item.put("crs", srid);
        return Optional.of(item);
    }

    private String sanitizeLabel(String display) {
        if (display == null) {
            return "";
        }
        String label = display.replace("(Adresse)", "");
        return label.replaceAll("\\s+", " ").trim();
    }
}
