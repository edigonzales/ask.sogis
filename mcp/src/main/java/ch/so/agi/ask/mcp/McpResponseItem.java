package ch.so.agi.ask.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import ch.so.agi.ask.model.MapAction;

/**
 * Normalisierte Antwortstruktur für MCP-Tools. Jedes Item enthält einen
 * {@code type}, die fachliche {@code payload} sowie optionale {@code options}
 * und {@code clientAction}-Definitionen. Die Hilfsfunktionen dienen dazu, die
 * Payload zu extrahieren und optionale Client-Aktionen in {@link MapAction}s
 * zu übersetzen.
 */
public record McpResponseItem(String type, Map<String, Object> payload, List<Map<String, Object>> options,
        Map<String, Object> clientAction) {

    public McpResponseItem(String type, Map<String, Object> payload, List<Map<String, Object>> options,
            Map<String, Object> clientAction) {
        Objects.requireNonNull(type, "type must not be null");
        this.type = type;
        this.payload = payload == null ? Map.of() : normalizeMap(payload);
        this.options = options == null ? List.of() : List.copyOf(options);
        this.clientAction = clientAction == null ? Map.of() : normalizeMap(clientAction);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("payload", payload);
        if (!options.isEmpty()) {
            map.put("options", options);
        }
        if (!clientAction.isEmpty()) {
            map.put("clientAction", clientAction);
        }
        return map;
    }

    public static McpResponseItem of(String type, Map<String, Object> payload) {
        return new McpResponseItem(type, payload, List.of(), Map.of());
    }

    public static List<Map<String, Object>> toMapList(List<McpResponseItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(McpResponseItem::toMap).toList();
    }

    public static String id(Map<String, Object> item) {
        Object id = payload(item).getOrDefault("id", item != null ? item.get("id") : null);
        return id != null ? String.valueOf(id) : null;
    }

    public static String label(Map<String, Object> item) {
        Object label = payload(item).getOrDefault("label", item != null ? item.get("label") : null);
        return label != null ? String.valueOf(label) : null;
    }

    public static String itemType(Map<String, Object> item) {
        Object type = item == null ? null : item.get("type");
        return type != null ? String.valueOf(type) : null;
    }

    public static Map<String, Object> payload(Map<String, Object> item) {
        if (item == null) {
            return Map.of();
        }
        Object payload = item.get("payload");
        if (payload instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        return normalizeMap(item);
    }

    public static List<MapAction> clientActions(Map<String, Object> item) {
        if (item == null) {
            return List.of();
        }
        Object clientAction = item.get("clientAction");
        return toMapActions(clientAction);
    }

    private static List<MapAction> toMapActions(Object clientAction) {
        if (clientAction == null) {
            return List.of();
        }
        if (clientAction instanceof Map<?, ?> map) {
            return actionFromMap(normalizeMap(map)).map(List::of).orElse(List.of());
        }
        if (clientAction instanceof List<?> list) {
            List<MapAction> actions = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    actionFromMap(normalizeMap(map)).ifPresent(actions::add);
                }
            }
            return actions;
        }
        return List.of();
    }

    private static Optional<MapAction> actionFromMap(Map<String, Object> map) {
        Object type = map.get("type");
        Object payload = map.get("payload");
        if (type instanceof String t && payload instanceof Map<?, ?> p) {
            return Optional.of(new MapAction(t, normalizeMap(p)));
        }
        return Optional.empty();
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((k, v) -> normalized.put(String.valueOf(k), v));
        return normalized;
    }
}
