package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LayerToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapLayers_expandsLayerGroups() throws Exception {
        String json = """
                {
                  "results": [
                    {
                      "dataproduct": {
                        "dataproduct_id": "ch.so.afu.abbaustellen",
                        "display": "Abbaustellen",
                        "type": "singleactor"
                      }
                    },
                    {
                      "dataproduct": {
                        "dataproduct_id": "ch.so.awjf.waldplan",
                        "display": "Waldplan",
                        "type": "layergroup",
                        "sublayers": [
                          {
                            "dataproduct_id": "ch.so.awjf.waldplan.waldplantyp",
                            "display": "Wald - Typ",
                            "type": "singleactor"
                          },
                          {
                            "dataproduct_id": "ch.so.awjf.waldplan.waldfunktion",
                            "display": "Wald - Funktion",
                            "type": "singleactor"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        LayerTools tools = new LayerTools(RestClient.builder(), mapper);
        List<McpResponseItem> items = tools.mapLayers(json);

        assertThat(items).hasSize(4);
        Map<String, Object> groupPayload = McpResponseItem.payload(items.get(1).toMap());
        assertThat(groupPayload.get("label")).isEqualTo("Waldplan (Gruppe)");
        assertThat(groupPayload.get("sublayers")).isInstanceOf(List.class);

        Map<String, Object> sublayerPayload = McpResponseItem.payload(items.get(2).toMap());
        assertThat(sublayerPayload.get("layerId")).isEqualTo("ch.so.awjf.waldplan.waldplantyp");
        assertThat(sublayerPayload.get("source")).isInstanceOf(Map.class);
        Map<String, Object> source = (Map<String, Object>) sublayerPayload.get("source");
        assertThat(source.get("LAYERS")).isEqualTo("ch.so.awjf.waldplan.waldplantyp");
    }

    @Test
    void searchLayers_returnsSuccessForSingleResult() {
        String json = """
                {
                  "results": [
                    {
                      "dataproduct": {
                        "dataproduct_id": "ch.so.afu.abbaustellen",
                        "display": "Abbaustellen",
                        "type": "singleactor"
                      }
                    }
                  ]
                }
                """;

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("searchtext=wald")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        LayerTools tools = new LayerTools(builder, mapper);
        LayerTools.LayerResult result = tools.searchLayers(Map.of("query", "wald"));

        server.verify();
        assertThat(result.status()).isEqualTo(ToolResult.Status.SUCCESS);
        assertThat(result.items()).hasSize(1);
        Map<String, Object> payload = McpResponseItem.payload(result.items().getFirst());
        assertThat(payload.get("layerId")).isEqualTo("ch.so.afu.abbaustellen");
    }
}
