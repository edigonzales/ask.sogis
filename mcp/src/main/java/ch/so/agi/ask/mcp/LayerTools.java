package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.so.agi.ask.mcp.McpToolArgSchema;
import ch.so.agi.ask.mcp.ToolResult.Status;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LayerTools {

    private static final Logger log = LoggerFactory.getLogger(LayerTools.class);

    public record LayerResult(
            Status status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    @McpTool(
            name = "layers.search",
            description = "Dummy search for map layers. Returns mocked WMTS + WMS layers."
    )
    public LayerResult searchLayers(@McpToolArgSchema("{ 'query': 'string - name of the desired map layer' }") Map<String,Object> args) {

        String query = (String) args.getOrDefault("query", "");
        log.info("MCP layers-search called with query={}", query);

        Map<String,Object> layer1Payload = Map.of(
                "id", "gsk-wmts",
                "label", "Gewässerschutzkarte (Kanton SO) – WMTS",
                "layerId", "ch.so.afu.gewaesserschutz",
                "type", "wmts",
                "confidence", 0.95,
                "source", Map.of(
                        "url", "https://example.ch/wmts/1.0.0/WMTSCapabilities.xml",
                        "layer", "ch.so.afu.gewaesserschutz",
                        "matrixSet", "EPSG:2056",
                        "format", "image/png",
                        "style", "default",
                        "tileSize", 256
                )
        );

        Map<String,Object> layer2Payload = Map.of(
                "id", "gsk-wms",
                "label", "Gewässerschutzkarte (CH) – WMS",
                "layerId", "ch.bafu.gewaesserschutz",
                "type", "wms",
                "confidence", 0.80,
                "source", Map.of(
                        "url", "https://example.ch/wms",
                        "layers", "ch.bafu.gewaesserschutz",
                        "format", "image/png",
                        "version", "1.3.0",
                        "transparent", true
                )
        );

        Map<String, Object> clientAction1 = Map.of("type", "addLayer",
                "payload", Map.of("id", layer1Payload.get("layerId"), "type", "wmts", "source", layer1Payload.get("source"),
                        "visible", true));
        Map<String, Object> clientAction2 = Map.of("type", "addLayer",
                "payload", Map.of("id", layer2Payload.get("layerId"), "type", "wms", "source", layer2Payload.get("source"),
                        "visible", true));

        List<Map<String, Object>> items = McpResponseItem.toMapList(List.of(
                new McpResponseItem("layer", layer1Payload, List.of(), clientAction1),
                new McpResponseItem("layer", layer2Payload, List.of(), clientAction2)
        ));

        return new LayerResult(Status.NEEDS_USER_CHOICE, items,
                "Gefundene Layer zu \"" + query + "\" (Mock).");
    }
}
