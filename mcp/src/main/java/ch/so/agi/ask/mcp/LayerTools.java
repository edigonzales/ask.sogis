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

import ch.so.agi.ask.mcp.ToolResult.Status;

@Component
public class LayerTools {

    private static final Logger log = LoggerFactory.getLogger(LayerTools.class);
    private static final String SEARCH_BASE_URL = "https://geo.so.ch/api/search/v2/";
    private static final String WMS_BASE_URL = "https://geo.so.ch/api/wms";
    private static final String CRS = "EPSG:2056";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LayerTools(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(SEARCH_BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    public record LayerResult(
            Status status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    @McpTool(
            name = "layers.search",
            description = "Sucht Kartenlayer (aka WMS layer) in der Geodateninfrastruktur des Kantons Solothurn."
    )
    public LayerResult searchLayers(@McpToolArgSchema("{ 'query': 'string - name of the desired map layer' }") Map<String,Object> args) {

        String query = Optional.ofNullable(args.get("query")).map(Object::toString).orElse("").trim();
        if (query.isBlank()) {
            return new LayerResult(Status.ERROR, List.of(), "Query ist erforderlich.");
        }

        log.info("MCP layers-search called with query={}", query);

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.queryParam("filter", "foreground")
                            .queryParam("limit", 25)
                            .queryParam("searchtext", query)
                            .build())
                    .retrieve()
                    .body(String.class);

            List<McpResponseItem> items = mapLayers(body);
            if (items.isEmpty()) {
                return new LayerResult(Status.ERROR, List.of(),
                        "Keine Layer zu \"" + query + "\" gefunden.");
            }

            Status status = items.size() > 1 ? Status.NEEDS_USER_CHOICE : Status.SUCCESS;
            String message = status == Status.SUCCESS
                    ? "Layer gefunden."
                    : "Mehrere Layer gefunden. Bitte Auswahl treffen.";
            return new LayerResult(status, McpResponseItem.toMapList(items), message);
        } catch (RestClientResponseException e) {
            log.warn("Layer search call failed with status {}", e.getStatusCode(), e);
            return new LayerResult(Status.ERROR, List.of(),
                    "Layer-Suche antwortete nicht erfolgreich (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            log.error("Layer search request failed", e);
            return new LayerResult(Status.ERROR, List.of(), "Layer-Suche konnte nicht erreicht werden.");
        } catch (IOException e) {
            log.error("Failed to parse layer search response", e);
            return new LayerResult(Status.ERROR, List.of(), "Antwort der Layer-Suche konnte nicht verarbeitet werden.");
        }
    }

    List<McpResponseItem> mapLayers(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<McpResponseItem> items = new ArrayList<>();
        for (JsonNode result : results) {
            JsonNode dataproduct = result.path("dataproduct");
            if (dataproduct == null || dataproduct.isMissingNode()) {
                continue;
            }
            String type = dataproduct.path("type").asText("");
            if ("layergroup".equalsIgnoreCase(type)) {
                items.addAll(mapLayerGroup(dataproduct));
            } else {
                McpResponseItem item = mapSingleLayer(dataproduct);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        return items;
    }

    private List<McpResponseItem> mapLayerGroup(JsonNode group) {
        String groupId = group.path("dataproduct_id").asText("");
        String groupLabel = group.path("display").asText(groupId);
        JsonNode sublayers = group.path("sublayers");
        if (sublayers == null || !sublayers.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> sublayerPayloads = new ArrayList<>();
        List<McpResponseItem> items = new ArrayList<>();
        for (JsonNode sublayer : sublayers) {
            McpResponseItem item = mapSingleLayer(sublayer);
            if (item == null) {
                continue;
            }
            sublayerPayloads.add(McpResponseItem.payload(item.toMap()));
            items.add(item);
        }

        if (sublayerPayloads.isEmpty()) {
            return items;
        }

        Map<String, Object> groupPayload = new LinkedHashMap<>();
        groupPayload.put("id", groupId + "::group");
        groupPayload.put("label", groupLabel + " (Gruppe)");
        groupPayload.put("type", "wms-group");
        groupPayload.put("layerId", groupId);
        groupPayload.put("sublayers", sublayerPayloads);
        items.add(0, new McpResponseItem("layer", groupPayload, List.of(), Map.of()));
        return items;
    }

    private McpResponseItem mapSingleLayer(JsonNode node) {
        String id = node.path("dataproduct_id").asText("");
        if (id.isBlank()) {
            return null;
        }
        String label = node.path("display").asText(id);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("url", WMS_BASE_URL);
        source.put("LAYERS", id);
        source.put("FORMAT", "image/png");
        source.put("VERSION", "1.3.0");
        source.put("TRANSPARENT", true);
        source.put("CRS", CRS);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("label", label);
        payload.put("layerId", id);
        payload.put("type", "wms");
        payload.put("crs", CRS);
        payload.put("source", source);

        return new McpResponseItem("layer", payload, List.of(), Map.of());
    }
}
