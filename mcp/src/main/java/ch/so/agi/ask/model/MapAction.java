package ch.so.agi.ask.model;

import java.util.Map;

public record MapAction(String type, // z.B. setView, addMarker, addLayer, …
        Map<String, Object> payload) {
}
