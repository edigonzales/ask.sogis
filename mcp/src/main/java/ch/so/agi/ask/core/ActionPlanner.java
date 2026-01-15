package ch.so.agi.ask.core;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.*;
import ch.so.agi.ask.mcp.McpResponseItem;

import java.util.*;

import static ch.so.agi.ask.model.IntentType.*;

/**
 * Übersetzt Intent + {@link PlannerOutput.Result} in eine ausführbare
 * {@link ActionPlan}. Hier werden MapActions nach Intent-Vorlage erzeugt und
 * Choice-Optionen vorbereitet, damit der Client die im README beschriebenen
 * Interaktionen (MapActions &amp; Choices) unmittelbar anwenden kann.
 */
@Service
public class ActionPlanner {

    /**
     * Formt die MCP-Ergebnisse in eine {@link ActionPlan} um und wählt den passenden
     * Status für den Client ("success" ⇔ {@code ok}, {@code needs_user_choice},
     * {@code needs_clarification}, {@code error} analog README).
     */
    public ActionPlan toActionPlan(IntentType intent, PlannerOutput.Result result) {
        if (result == null) {
            return ActionPlan.error("Keine Resultate vom MCP/Planner.");
        }
        List<Map<String, Object>> items = Optional.ofNullable(result.items()).orElse(List.of());

        if ("ok".equals(result.status()) && items.size() == 1) {
            Map<String, Object> item = items.get(0);
            return ActionPlan.ok(mapActions(intent, item), "Erledigt.");
        }
        if ("needs_user_choice".equals(result.status()) || ("ok".equals(result.status()) && items.size() > 1)) {
            // Choice-Erzeugung: mehrere Kandidaten ⇒ interaktive Auswahl mit Intent-basiertem Label
            List<Choice> choices = items.stream()
                    .map(i -> {
                        Map<String, Object> payload = McpResponseItem.payload(i);
                        String id = Optional.ofNullable(McpResponseItem.id(i)).orElse(UUID.randomUUID().toString());
                        String label = Optional.ofNullable(McpResponseItem.label(i))
                                .orElse((intent != null ? intent.id() : "intent") + " option");
                        Double confidence = null;
                        Object conf = payload.get("confidence");
                        if (conf instanceof Number n) {
                            confidence = n.doubleValue();
                        }
                        return new Choice(id, label, confidence, mapActions(intent, i), payload);
                    })
                    .toList();
            String message = Optional.ofNullable(result.message()).orElse("Bitte wähle eine Option.");
            return ActionPlan.needsUserChoice(choices, message);
        }
        if ("needs_clarification".equals(result.status())) {
            return ActionPlan.needsClarification(result.message());
        }
        return ActionPlan.error(Optional.ofNullable(result.message()).orElse("Unbekannter Status."));
    }

    // Templates pro Intent: erzeugt MapActions wie im README dokumentiert (setView, addLayer, addMarker …)
    private List<MapAction> template(IntentType intent, Map<String, Object> payload) {
        if (intent == null) {
            return List.of();
        }
        return switch (intent) {
        case GOTO_ADDRESS -> {
            var coord = (List<?>) payload.get("coord"); // [x,y]
            var crs = (String) payload.getOrDefault("crs", "EPSG:2056");
            var id = (String) payload.getOrDefault("id", "addr");
            var label = (String) payload.getOrDefault("label", "");
            yield List.of(new MapAction("setView", Map.of("center", coord, "zoom", 17, "crs", crs)),
                    new MapAction("addMarker", Map.of("id", "addr-" + id, "coord", coord, "style", "pin-default", "label", label)));
        }
        case LOAD_LAYER -> {
            List<MapAction> actions = new ArrayList<>();
            Object sublayers = payload.get("sublayers");
            if (sublayers instanceof List<?> sublayerList && !sublayerList.isEmpty()) {
                for (Object entry : sublayerList) {
                    if (entry instanceof Map<?, ?> sublayerMap) {
                        actions.add(buildAddLayerAction((Map<String, Object>) sublayerMap));
                    }
                }
                yield actions;
            }

            actions.add(buildAddLayerAction(payload));
            yield actions;
        }
        case OEREB_EXTRACT -> {
            var egrid = (String) Optional.ofNullable(payload.get("egrid")).orElse(payload.get("id"));
            List<MapAction> actions = new ArrayList<>();
            var coord = (List<?>) payload.get("coord");
            if (coord != null) {
                actions.add(new MapAction("setView", Map.of("center", coord, "zoom", 17, "crs", "EPSG:2056")));
                actions.add(new MapAction("addMarker",
                        Map.of("id", "oereb-" + egrid, "coord", coord, "style", "pin-default", "label", payload.get("label"))));
            }
            var geometry = payload.get("geometry");
            if (geometry != null) {
                actions.add(new MapAction("addLayer",
                        Map.of("id", "oereb-highlight-" + egrid, "type", "geojson",
                                "source", Map.of("data", geometry, "style", "highlight"))));
            }
            yield actions;
        }
        case GEOTHERMAL_PROBE_ASSESSMENT -> {
            var coord = (List<?>) payload.get("coord");
            var label = (String) payload.getOrDefault("label", "Geothermal probe assessment");
            if (coord == null) {
                yield List.of();
            }
            yield List.of(
                    new MapAction("setView", Map.of("center", coord, "zoom", 17, "crs", "EPSG:2056")),
                    new MapAction("addMarker",
                            Map.of("id", "geothermal-" + payload.getOrDefault("id", "probe"), "coord", coord,
                                    "style", "pin-default", "label", label)));
        }
        case CADASTRAL_PLAN -> {
            List<MapAction> actions = new ArrayList<>();
            List<Double> extent = McpResponseItem.extent(Map.of("payload", payload));
            List<Double> center = extent.size() >= 4
                    ? List.of((extent.get(0) + extent.get(2)) / 2.0, (extent.get(1) + extent.get(3)) / 2.0)
                    : McpResponseItem.centroid(Map.of("payload", payload));

            if (!center.isEmpty()) {
                actions.add(new MapAction("setView",
                        Map.of("center", center, "zoom", 17, "crs", payload.getOrDefault("crs", "EPSG:2056"))));
            }
            var geometry = payload.get("geometry");
            if (geometry != null) {
                actions.add(new MapAction("addLayer",
                        Map.of("id", "cadastral-plan-" + payload.getOrDefault("id", "plan"), "type", "geojson",
                                "source", Map.of("data", geometry, "style", "highlight"))));
            }
            yield actions;
        }
        default -> List.of();
        };
    }

    private List<MapAction> mapActions(IntentType intent, Map<String, Object> item) {
        Map<String, Object> payload = McpResponseItem.payload(item);
        Set<MapAction> actions = new LinkedHashSet<>(McpResponseItem.clientActions(item));
        actions.addAll(template(intent, payload));
        return List.copyOf(actions);
    }

    private MapAction buildAddLayerAction(Map<String, Object> payload) {
        var layerId = (String) payload.getOrDefault("layerId", payload.get("id"));
        var type = (String) payload.get("type");
        var source = (Map<String, Object>) payload.get("source");
        var label = payload.get("label");
        Map<String, Object> actionPayload = new LinkedHashMap<>();
        actionPayload.put("id", layerId);
        actionPayload.put("type", type);
        actionPayload.put("source", source);
        actionPayload.put("visible", true);
        if (label != null) {
            actionPayload.put("label", label);
        }
        return new MapAction("addLayer", actionPayload);
    }
}
