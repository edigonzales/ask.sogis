package ch.so.agi.ask.model;

import java.util.List;

public record Choice(String id, String label, Double confidence, List<MapAction> mapActions, Object data) {
}
