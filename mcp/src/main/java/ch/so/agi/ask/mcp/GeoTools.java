package ch.so.agi.ask.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GeoTools {

    private static final Logger log = LoggerFactory.getLogger(GeoTools.class);

    public record GeoResult(
            String status,
            List<Map<String,Object>> items,
            String message
    ) implements ToolResult {}

    /**
     * Dummy geocoder tool.
     */
    @McpTool(
            name = "geo.geocode",
            description = "Dummy geocoder for addresses. Returns 1 or 2 mock results."
    )
    public GeoResult geocode(Map<String, Object> args) {

        String q = (String) args.getOrDefault("q", "");
        log.info("MCP geo-geocode called with q={}", q);

        if (q.toLowerCase().contains("langendorfstrasse 19b")) {

            Map<String,Object> item = new HashMap<>();
            item.put("id", "sol-19b");
            item.put("label", "Langendorfstrasse 19b, 4500 Solothurn");
            item.put("coord", List.of(2600000.12, 1200000.34)); 
            item.put("crs", "EPSG:2056");
            item.put("confidence", 0.95);

            return new GeoResult(
                    "ok",
                    List.of(item),
                    "Adresse eindeutig gefunden (Mock)."
            );
        }

        Map<String,Object> item1 = new HashMap<>();
        item1.put("id", "match-1");
        item1.put("label", q + " (Variante 1)");
        item1.put("coord", List.of(2600000.0, 1200000.0));
        item1.put("crs", "EPSG:2056");
        item1.put("confidence", 0.7);

        Map<String,Object> item2 = new HashMap<>();
        item2.put("id", "match-2");
        item2.put("label", q + " (Variante 2)");
        item2.put("coord", List.of(2600100.0, 1200100.0));
        item2.put("crs", "EPSG:2056");
        item2.put("confidence", 0.6);

        return new GeoResult(
                "ok",
                List.of(item1, item2),
                "Mehrere Adress-Treffer (Mock)."
        );
    }
}

