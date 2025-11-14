package ch.so.agi.ask.core;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.*;

import java.util.*;

@Service
public class ActionPlanner {

    public ActionPlan toActionPlan(String intent, PlannerOutput.Result result) {
        if (result == null) {
            return ActionPlan.error("Keine Resultate vom MCP/Planner.");
        }
        List<Map<String, Object>> items = Optional.ofNullable(result.items()).orElse(List.of());

        if ("ok".equals(result.status()) && items.size() == 1) {
            return ActionPlan.ok(template(intent, items.get(0)), "Erledigt.");
        }
        if ("ok".equals(result.status()) && items.size() > 1) {
            List<Choice> choices = items.stream()
                    .map(i -> new Choice((String) i.getOrDefault("id", UUID.randomUUID().toString()),
                            (String) i.getOrDefault("label", intent + " option"),
                            (Double) i.getOrDefault("confidence", null), template(intent, i), i))
                    .toList();
            return ActionPlan.needsUserChoice(choices, "Bitte wähle eine Option.");
        }
        if ("needs_clarification".equals(result.status())) {
            return ActionPlan.needsClarification(result.message());
        }
        return ActionPlan.error(Optional.ofNullable(result.message()).orElse("Unbekannter Status."));
    }

    // Templates pro Intent – hier nur zwei Beispiele
    private List<MapAction> template(String intent, Map<String, Object> item) {
        return switch (intent) {
        case "goto_address" -> {
            var coord = (List<?>) item.get("coord"); // [x,y]
            var crs = (String) item.getOrDefault("crs", "EPSG:2056");
            var id = (String) item.getOrDefault("id", "addr");
            yield List.of(new MapAction("setView", Map.of("center", coord, "zoom", 17, "crs", crs)),
                    new MapAction("addMarker", Map.of("id", "addr-" + id, "coord", coord, "style", "pin-default")));
        }
        case "load_layer" -> {
            var layerId = (String) item.get("layerId");
            var type = (String) item.get("type"); // wmts|wms|vector|geojson
            var source = (Map<String, Object>) item.get("source");
            yield List.of(
                    new MapAction("addLayer", Map.of("id", layerId, "type", type, "source", source, "visible", true)));
        }
        default -> List.of();
        };
    }
}
