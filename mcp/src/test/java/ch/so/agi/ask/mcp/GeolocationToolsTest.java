package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;

import ch.so.agi.ask.mcp.McpResponseItem;
import ch.so.agi.ask.mcp.ToolResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeolocationToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapResults_usesBboxAndCleansLabel() throws Exception {
        String json = """
                {"results":[{"feature":{"bbox":[2605899,1229278,2605899,1229278],"display":"Langendorfstrasse 19b, 4500 Solothurn (Adresse)","feature_id":"623490242","srid":"EPSG:2056"}}]}
                """;
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = geolocationTools.mapResults(
                mapper.readTree(json).path("results"),
                label -> label.replace("(Adresse)", "").replaceAll("\\s+", " ").trim(),
                false);

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
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = geolocationTools.mapResults(
                mapper.readTree(json).path("results"),
                label -> label.replace("(Adresse)", "").replaceAll("\\s+", " ").trim(),
                false);
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

    @Test
    void mapResults_includesDisplayNameAndBboxForMunicipality() throws Exception {
        String json = """
                {"results":[{"feature":{"bbox":[2592561,1223174,2599482,1232183],"display":"Grenchen | 2546 (Gemeinde)","feature_id":"580264","srid":"EPSG:2056"}}]}
                """;
        GeolocationTools geolocationTools = new GeolocationTools(RestClient.builder(), mapper);

        List<McpResponseItem> items = geolocationTools.mapResults(
                mapper.readTree(json).path("results"),
                label -> label.replaceAll("\\s+", " ").trim(),
                true);

        assertEquals(1, items.size());
        Map<String, Object> payload = McpResponseItem.payload(items.get(0).toMap());
        assertEquals("Grenchen | 2546 (Gemeinde)", payload.get("label"));
        assertEquals("Grenchen | 2546 (Gemeinde)", payload.get("displayName"));
        assertEquals(List.of(2592561d, 1223174d, 2599482d, 1232183d), payload.get("bbox"));
    }

    @Test
    void geocodeMunicipality_returnsChoicesWhenMultipleMatches() throws Exception {
        String json = """
                {"results":[{"feature":{"bbox":[2592561,1223174,2599482,1232183],"display":"Grenchen | 2546 (Gemeinde)","feature_id":"580264","srid":"EPSG:2056"}},{"feature":{"bbox":[2600000,1220000,2605000,1225000],"display":"Grenchenberg | 2547 (Gemeinde)","feature_id":"580265","srid":"EPSG:2056"}}]}
                """;

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("searchtext=Grenchen")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        GeolocationTools geolocationTools = new GeolocationTools(builder, mapper);
        GeolocationTools.GeolocationResult result = geolocationTools.geocodeMunicipality(Map.of("q", "Grenchen"));

        server.verify();
        assertEquals(ToolResult.Status.NEEDS_USER_CHOICE, result.status());
        assertEquals(2, result.items().size());
        Map<String, Object> payload = McpResponseItem.payload(result.items().getFirst());
        assertEquals(List.of(2592561d, 1223174d, 2599482d, 1232183d), payload.get("extent"));
    }
}
