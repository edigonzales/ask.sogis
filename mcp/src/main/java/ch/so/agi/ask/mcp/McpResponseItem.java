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
 * und {@code clientAction}-Definitionen. Die Payload kann optionale
 * Geometrieangaben ({@code geometry}, {@code centroid}, {@code extent})
 * enthalten, damit komplexe Flächen- oder Linienobjekte konsistent auf der
 * Karte genutzt werden können. Die Hilfsfunktionen dienen dazu, die Payload zu
 * extrahieren, Geometriedaten herzuleiten und optionale Client-Aktionen in
 * {@link MapAction}s zu übersetzen.
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

    public static Map<String, Object> geometry(Map<String, Object> item) {
        Map<String, Object> payload = payload(item);
        Object geometry = payload.get("geometry");
        if (geometry instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        return Map.of();
    }

    public static List<Double> centroid(Map<String, Object> item) {
        Map<String, Object> payload = payload(item);
        List<Double> centroid = normalizeCoordList(payload.getOrDefault("centroid", payload.get("coord")), 2);
        if (!centroid.isEmpty()) {
            return centroid;
        }
        Map<String, Object> geometry = geometry(item);
        if (!geometry.isEmpty()) {
            return deriveCentroid(geometry);
        }
        return List.of();
    }

    public static List<Double> extent(Map<String, Object> item) {
        Map<String, Object> payload = payload(item);
        List<Double> extent = normalizeCoordList(payload.get("extent"), 4);
        if (!extent.isEmpty()) {
            return extent;
        }
        Map<String, Object> geometry = geometry(item);
        if (!geometry.isEmpty()) {
            List<Double> derived = deriveExtent(geometry);
            if (!derived.isEmpty()) {
                return derived;
            }
        }
        List<Double> coords = normalizeCoordList(payload.get("coord"), 4);
        return coords.isEmpty() ? List.of() : coords;
    }

    public static Map<String, Object> normalizeGeometry(Object geometry) {
        if (geometry instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        return Map.of();
    }

    public static List<Double> deriveCentroid(Map<String, Object> geometry) {
        List<List<Double>> coords = new ArrayList<>();
        collectCoordinates(geometry.get("coordinates"), coords);
        if (coords.isEmpty()) {
            return List.of();
        }
        double sumX = 0d;
        double sumY = 0d;
        int count = 0;
        for (List<Double> coord : coords) {
            if (coord.size() < 2) {
                continue;
            }
            sumX += coord.get(0);
            sumY += coord.get(1);
            count++;
        }
        if (count == 0) {
            return List.of();
        }
        return List.of(sumX / count, sumY / count);
    }

    public static List<Double> deriveExtent(Map<String, Object> geometry) {
        List<List<Double>> coords = new ArrayList<>();
        collectCoordinates(geometry.get("coordinates"), coords);
        if (coords.isEmpty()) {
            return List.of();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (List<Double> coord : coords) {
            if (coord.size() < 2) {
                continue;
            }
            minX = Math.min(minX, coord.get(0));
            minY = Math.min(minY, coord.get(1));
            maxX = Math.max(maxX, coord.get(0));
            maxY = Math.max(maxY, coord.get(1));
        }
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(maxX) || Double.isInfinite(maxY)) {
            return List.of();
        }
        return List.of(minX, minY, maxX, maxY);
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

    private static List<Double> normalizeCoordList(Object raw, int minSize) {
        if (!(raw instanceof List<?> list) || list.size() < minSize) {
            return List.of();
        }
        List<Double> coords = new ArrayList<>();
        for (Object entry : list) {
            Double value = asDouble(entry);
            if (value != null) {
                coords.add(value);
            }
        }
        return coords.size() < minSize ? List.of() : coords;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private static void collectCoordinates(Object node, List<List<Double>> coords) {
        if (node instanceof List<?> list) {
            if (list.size() >= 2 && list.get(0) instanceof Number && list.get(1) instanceof Number) {
                Double x = asDouble(list.get(0));
                Double y = asDouble(list.get(1));
                if (x != null && y != null) {
                    coords.add(List.of(x, y));
                }
            } else {
                list.forEach(child -> collectCoordinates(child, coords));
            }
        }
    }
}
