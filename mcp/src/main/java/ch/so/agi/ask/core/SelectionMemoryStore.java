package ch.so.agi.ask.core;

import java.util.Map;
import java.util.Optional;

public interface SelectionMemoryStore {
    Optional<Map<String, Object>> get(String sessionId);

    void save(String sessionId, Map<String, Object> selection);

    void clear(String sessionId);
}
