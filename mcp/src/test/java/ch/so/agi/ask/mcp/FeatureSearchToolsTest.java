package ch.so.agi.ask.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import ch.so.agi.ask.mcp.McpResponseItem;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureSearchToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapFeatures_extractsEgridAndMetadata() throws Exception {
        String json = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "id": 681114440,
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[2600583.833, 1215641.602], [2600584.541, 1215654.526], [2600604.65, 1215647.679]]]
                      },
                      "properties": {
                        "nummer": "168",
                        "egrid": "CH807306583219",
                        "grundbuch": "Messen",
                        "art_txt": "Liegenschaft",
                        "gemeinde": "Messen"
                      }
                    },
                    {
                      "type": "Feature",
                      "id": 681114305,
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[2599650.273, 1217214.514], [2599668.072, 1217223.788], [2599680.974, 1217231.63]]]
                      },
                      "properties": {
                        "nummer": "168",
                        "egrid": "CH181832067404",
                        "grundbuch": "Balm bei Messen",
                        "art_txt": "Liegenschaft",
                        "gemeinde": "Messen"
                      }
                    }
                  ]
                }
                """;

        FeatureSearchTools tools = new FeatureSearchTools(RestClient.builder(), mapper);
        List<McpResponseItem> items = tools.mapFeatures(json);

        assertThat(items).hasSize(2);
        Map<String, Object> first = McpResponseItem.payload(items.getFirst().toMap());
        assertThat(first.get("egrid")).isEqualTo("CH807306583219");
        assertThat(first.get("municipality")).isEqualTo("Messen");
        assertThat(first.get("landRegister")).isEqualTo("Messen");
        assertThat(first.get("propertyType")).isEqualTo("Liegenschaft");
        assertThat(first.get("label")).asString().contains("168").contains("Messen").contains("CH807306583219");
        assertThat(first.get("geometry")).isInstanceOf(Map.class);
        assertThat(first.get("coord")).isInstanceOf(List.class);
    }
}
