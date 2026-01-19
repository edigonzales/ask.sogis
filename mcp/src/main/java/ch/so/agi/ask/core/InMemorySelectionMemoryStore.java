package ch.so.agi.ask.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemorySelectionMemoryStore implements SelectionMemoryStore {

    private final Map<String, Map<String, Object>> selections = new ConcurrentHashMap<>();

    @Override
    public Optional<Map<String, Object>> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(selections.get(sessionId));
    }

    @Override
    public void save(String sessionId, Map<String, Object> selection) {
        if (sessionId == null || sessionId.isBlank() || selection == null || selection.isEmpty()) {
            return;
        }
        selections.put(sessionId, new HashMap<>(selection));
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        selections.remove(sessionId);
    }
}
