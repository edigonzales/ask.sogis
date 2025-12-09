package ch.so.agi.ask.core;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

public interface ChatMemoryStore {
    List<Message> getMessages(String sessionId);

    void appendMessage(String sessionId, Message message);

    void appendMessages(String sessionId, List<Message> messages);

    void deleteSession(String sessionId);
}
