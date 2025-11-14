package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LayerTools {

    private static final Logger log = LoggerFactory.getLogger(LayerTools.class);

    public record LayerResult(
            String status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    @McpTool(
            name = "layers-search",
            description = "Dummy search for map layers. Returns mocked WMTS + WMS layers."
    )
    public LayerResult searchLayers(Map<String,Object> args) {

        String query = (String) args.getOrDefault("query", "");
        log.info("MCP layers-search called with query={}", query);

        Map<String,Object> layer1 = Map.of(
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

        Map<String,Object> layer2 = Map.of(
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

        return new LayerResult(
                "ok",
                List.of(layer1, layer2),
                "Gefundene Layer zu \"" + query + "\" (Mock)."
        );
    }
}

