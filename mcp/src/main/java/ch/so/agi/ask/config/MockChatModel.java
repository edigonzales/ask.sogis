package ch.so.agi.ask.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Fallback {@link ChatModel}, die ohne LLM-Backend auskommt und deterministische
 * Antworten liefert. Wird automatisch verwendet, wenn keine OpenAI-API-Keys
 * konfiguriert sind (z.B. im Testlauf oder bei lokalem Development ohne Cloud-
 * Zugriff).
 */
class MockChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MockChatModel.class);

    private static final String DEFAULT_RESPONSE = """
            {
              "requestId": "mock-request",
              "steps": [
                {
                  "intent": "noop",
                  "toolCalls": [],
                  "result": {
                    "status": "pending",
                    "items": [],
                    "message": ""
                  }
                }
              ]
            }
            """;

    @Override
    public ChatResponse call(Prompt prompt) {
        log.info("Using MockChatModel – returning deterministic planner result");
        var message = AssistantMessage.builder().content(DEFAULT_RESPONSE).build();
        return new ChatResponse(List.of(new Generation(message)));
    }
}
