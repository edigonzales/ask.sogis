package ch.so.agi.ask.core;

import org.springframework.stereotype.Service;

import ch.so.agi.ask.model.*;

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
            return ActionPlan.ok(template(intent, items.get(0)), "Erledigt.");
        }
        if ("ok".equals(result.status()) && items.size() > 1) {
            // Choice-Erzeugung: mehrere Kandidaten ⇒ interaktive Auswahl mit Intent-basiertem Label
            List<Choice> choices = items.stream()
                    .map(i -> new Choice((String) i.getOrDefault("id", UUID.randomUUID().toString()),
                            (String) i.getOrDefault("label", (intent != null ? intent.id() : "intent") + " option"),
                            (Double) i.getOrDefault("confidence", null), template(intent, i), i))
                    .toList();
            return ActionPlan.needsUserChoice(choices, "Bitte wähle eine Option.");
        }
        if ("needs_clarification".equals(result.status())) {
            return ActionPlan.needsClarification(result.message());
        }
        return ActionPlan.error(Optional.ofNullable(result.message()).orElse("Unbekannter Status."));
    }

    // Templates pro Intent: erzeugt MapActions wie im README dokumentiert (setView, addLayer, addMarker …)
    private List<MapAction> template(IntentType intent, Map<String, Object> item) {
        if (intent == null) {
            return List.of();
        }
        return switch (intent) {
        case GOTO_ADDRESS -> {
            var coord = (List<?>) item.get("coord"); // [x,y]
            var crs = (String) item.getOrDefault("crs", "EPSG:2056");
            var id = (String) item.getOrDefault("id", "addr");
            yield List.of(new MapAction("setView", Map.of("center", coord, "zoom", 17, "crs", crs)),
                    new MapAction("addMarker", Map.of("id", "addr-" + id, "coord", coord, "style", "pin-default")));
        }
        case LOAD_LAYER -> {
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
