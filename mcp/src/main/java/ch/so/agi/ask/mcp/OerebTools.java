package ch.so.agi.ask.mcp;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OerebTools {

    public record OerebResult(String status, List<Map<String, Object>> items, String message) implements ToolResult {
    }

    @McpTool(name = "oereb.egridByXY", description = "Mock: Resolves egrid(s) from a Swiss coordinate pair (LV95)")
    public OerebResult getOerebEgridByXY(
            @McpToolParam(description = "Coordinate input, expecting keys 'x' and 'y' or 'coord' array", required = true) Map<String, Object> args) {

        double x = asDouble(args.get("x"), 2600000d);
        double y = asDouble(args.get("y"), 1200000d);
        List<Double> coord = args.containsKey("coord") && args.get("coord") instanceof List<?> raw
                ? raw.stream().map(o -> asDouble(o, null)).filter(Objects::nonNull).toList()
                : List.of(x, y);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildEgrid("SO0200001234", "EGRID %s / Musterparzelle".formatted("SO0200001234"), coord));
        items.add(buildEgrid("SO0200005678", "EGRID %s / Alternative Fläche".formatted("SO0200005678"), coord));

        String message = items.size() > 1 ? "Mehrere Grundstücke gefunden." : "EGRID gefunden.";
        return new OerebResult("ok", items, message);
    }

    @McpTool(name = "oereb.extractById", description = "Mock: Returns an OEREB extract (PDF URL) for a given egrid")
    public OerebResult getOerebExtractById(@McpToolParam(description = "Must include 'egrid' or 'selection' with an id") Map<String, Object> args) {
        String egrid = extractEgrid(args);
        if (egrid == null || egrid.isBlank()) {
            return new OerebResult("error", List.of(), "Kein EGRID übergeben.");
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", egrid);
        item.put("egrid", egrid);
        item.put("label", "ÖREB-Auszug für %s".formatted(egrid));
        item.put("extractUrl", "https://example.com/oereb/%s.pdf".formatted(egrid));

        if (args.get("selection") instanceof Map<?, ?> selectionMap) {
            Object coord = selectionMap.get("coord");
            if (coord != null) {
                item.put("coord", coord);
            }
        }

        return new OerebResult("ok", List.of(item), "ÖREB-Auszug erstellt.");
    }

    private Map<String, Object> buildEgrid(String id, String label, List<Double> coord) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("egrid", id);
        item.put("label", label);
        item.put("coord", coord);
        item.put("crs", "EPSG:2056");
        return item;
    }

    private double asDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignore) {
                // ignore
            }
        }
        if (fallback != null) {
            return fallback;
        }
        return Double.NaN;
    }

    private String extractEgrid(Map<String, Object> args) {
        Object direct = args.getOrDefault("egrid", args.get("id"));
        if (direct instanceof String s && !s.isBlank()) {
            return s;
        }
        if (args.get("selection") instanceof Map<?, ?> selectionMap) {
            Object candidate = selectionMap.get("egrid");
            if (candidate instanceof String s && !s.isBlank()) {
                return s;
            }
            candidate = selectionMap.get("id");
            if (candidate instanceof String s2 && !s2.isBlank()) {
                return s2;
            }
        }
        return null;
    }
}
