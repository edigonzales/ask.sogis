package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import ch.so.agi.ask.mcp.McpResponseItem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeolocationToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapResults_usesBboxAndCleansLabel() throws Exception {
        String json = """
                {"results":[{"feature":{"bbox":[2605899,1229278,2605899,1229278],"display":"Langendorfstrasse 19b, 4500 Solothurn (Adresse)","feature_id":"623490242","srid":"EPSG:2056"}}]}
                """;
        JsonNode root = mapper.readTree(json);
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = geolocationTools.mapResults(root.path("results"));

        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0).toMap();
        Map<String, Object> payload = McpResponseItem.payload(item);
        assertEquals("623490242", payload.get("id"));
        assertEquals("Langendorfstrasse 19b, 4500 Solothurn", payload.get("label"));
        assertEquals("EPSG:2056", payload.get("crs"));
        assertEquals(List.of(2605899.0, 1229278.0), payload.get("coord"));
        assertEquals(List.of(2605899.0, 1229278.0), payload.get("centroid"));
        assertEquals(List.of(2605899.0, 1229278.0, 2605899.0, 1229278.0), payload.get("extent"));
        assertEquals("geolocation", item.get("type"));
        assertTrue(item.containsKey("clientAction"));
        Map<String, Object> clientAction = (Map<String, Object>) item.get("clientAction");
        Map<String, Object> actionPayload = (Map<String, Object>) clientAction.get("payload");
        assertEquals(payload.get("coord"), actionPayload.get("center"));
    }

    @Test
    void mapResults_returnsEmptyWhenMissingData() throws Exception {
        String json = """
                {"results":[{"feature":{"display":"", "feature_id":null}}]}
                """;
        JsonNode root = mapper.readTree(json);
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = geolocationTools.mapResults(root.path("results"));
        assertTrue(items.isEmpty());
    }

    @Test
    void filterExactMatches_prefersStreetAndHouseNumber() {
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = List.of(
                new McpResponseItem("geolocation", Map.of("label", "Burgunderstrasse 9, 4500 Solothurn"), List.of(),
                        Map.of()),
                new McpResponseItem("geolocation", Map.of("label", "Burgunderstrasse 19, 4500 Solothurn"), List.of(),
                        Map.of())
        );

        List<McpResponseItem> matches = geolocationTools.filterExactMatches("burgunderstrasse 9, solothurn", items);

        assertEquals(1, matches.size());
        assertEquals("Burgunderstrasse 9, 4500 Solothurn", McpResponseItem.payload(matches.get(0).toMap()).get("label"));
    }

    @Test
    void filterExactMatches_returnsEmptyForEmptyQuery() {
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);
        List<McpResponseItem> items = List.of(
                new McpResponseItem("geolocation", Map.of("label", "Teststrasse 1, 4500 Solothurn"), List.of(), Map.of())
        );

        assertTrue(geolocationTools.filterExactMatches(" ", items).isEmpty());
    }
}
