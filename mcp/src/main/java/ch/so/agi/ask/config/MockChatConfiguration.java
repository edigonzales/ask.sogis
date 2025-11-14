package ch.so.agi.ask.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.ai.model.SpringAIModelProperties;

/**
 * Stellt einen {@link ChatModel}-Fallback bereit, falls kein echter Anbieter
 * (z.B. OpenAI) konfiguriert ist. Aktiviert wird die Konfiguration, sobald das
 * Property {@code spring.ai.model.chat} auf {@code mock} gesetzt ist. Das
 * geschieht automatisch durch den {@link OpenAiEnvironmentPostProcessor}, wenn
 * keine API-Keys vorhanden sind.
 */
@Configuration
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = "mock")
class MockChatConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChatModel mockChatModel() {
        return new MockChatModel();
    }
}
