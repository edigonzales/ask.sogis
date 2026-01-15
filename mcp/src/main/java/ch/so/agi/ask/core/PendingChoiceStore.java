package ch.so.agi.ask.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import ch.so.agi.ask.model.PlannerOutput;

public interface PendingChoiceStore {
    Optional<PendingChoiceContext> consume(String sessionId);

    Optional<PendingChoiceContext> peek(String sessionId);

    void save(String sessionId, PendingChoiceContext context);

    void clear(String sessionId);

    record PendingChoiceContext(
            String requestId,
            PlannerOutput.Step step,
            int nextToolCallIndex,
            List<Map<String, Object>> choiceItems) {
    }
}
