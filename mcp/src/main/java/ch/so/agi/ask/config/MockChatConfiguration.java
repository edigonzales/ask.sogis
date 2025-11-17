package ch.so.agi.ask.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Stellt einen {@link ChatModel}-Fallback bereit, falls kein echter Anbieter
 * (z.B. OpenAI) konfiguriert ist. Die Konfiguration ist nur aktiv, wenn keine
 * API-Keys vorhanden sind – andernfalls greift automatisch die reguläre Spring
 * AI OpenAI-Auto-Konfiguration.
 */
@Configuration
@Conditional(OpenAiApiKeyMissingCondition.class)
class MockChatConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    ChatModel mockChatModel() {
        return new MockChatModel();
    }
}
