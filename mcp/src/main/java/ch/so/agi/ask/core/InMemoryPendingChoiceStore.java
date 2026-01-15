package ch.so.agi.ask.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryPendingChoiceStore implements PendingChoiceStore {

    private final Map<String, PendingChoiceContext> pending = new ConcurrentHashMap<>();

    @Override
    public Optional<PendingChoiceContext> consume(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.remove(sessionId));
    }

    @Override
    public Optional<PendingChoiceContext> peek(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pending.get(sessionId));
    }

    @Override
    public void save(String sessionId, PendingChoiceContext context) {
        if (sessionId == null || sessionId.isBlank() || context == null) {
            return;
        }
        pending.put(sessionId, context);
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        pending.remove(sessionId);
    }
}
