package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

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

        List<Map<String, Object>> items = geolocationTools.mapResults(root.path("results"));

        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals("623490242", item.get("id"));
        assertEquals("Langendorfstrasse 19b, 4500 Solothurn", item.get("label"));
        assertEquals("EPSG:2056", item.get("crs"));
        assertEquals(List.of(2605899.0, 1229278.0, 2605899.0, 1229278.0), item.get("coord"));
    }

    @Test
    void mapResults_returnsEmptyWhenMissingData() throws Exception {
        String json = """
                {"results":[{"feature":{"display":"", "feature_id":null}}]}
                """;
        JsonNode root = mapper.readTree(json);
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<Map<String, Object>> items = geolocationTools.mapResults(root.path("results"));
        assertTrue(items.isEmpty());
    }

    @Test
    void filterExactMatches_prefersStreetAndHouseNumber() {
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<Map<String, Object>> items = List.of(
                Map.of("label", "Burgunderstrasse 9, 4500 Solothurn"),
                Map.of("label", "Burgunderstrasse 19, 4500 Solothurn")
        );

        List<Map<String, Object>> matches = geolocationTools.filterExactMatches("burgunderstrasse 9, solothurn", items);

        assertEquals(1, matches.size());
        assertEquals("Burgunderstrasse 9, 4500 Solothurn", matches.get(0).get("label"));
    }

    @Test
    void filterExactMatches_returnsEmptyForEmptyQuery() {
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);
        List<Map<String, Object>> items = List.of(
                Map.of("label", "Teststrasse 1, 4500 Solothurn")
        );

        assertTrue(geolocationTools.filterExactMatches(" ", items).isEmpty());
    }
}
