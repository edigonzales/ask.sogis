package ch.so.agi.ask.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

@Component
public class InMemoryChatMemoryStore implements ChatMemoryStore {

    private final Map<String, List<Message>> memory = new ConcurrentHashMap<>();

    @Override
    public List<Message> getMessages(String sessionId) {
        return List.copyOf(memory.getOrDefault(sessionId, List.of()));
    }

    @Override
    public void appendMessage(String sessionId, Message message) {
        appendMessages(sessionId, List.of(message));
    }

    @Override
    public void appendMessages(String sessionId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        memory.compute(sessionId, (id, existing) -> {
            List<Message> updated = new ArrayList<>(existing == null ? List.of() : existing);
            updated.addAll(messages);
            return updated;
        });
    }

    @Override
    public void deleteSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        memory.remove(sessionId);
    }
}
