package ch.so.agi.ask.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Custom condition that matches whenever no OpenAI API key is configured.
 * This allows us to register mock AI components only for local development
 * or CI environments that do not have access to the real LLM backend.
 */
class OpenAiApiKeyMissingCondition extends SpringBootCondition {

    private static final String OPENAI_API_KEY_PROPERTY = "spring.ai.openai.api-key";

    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean missing = !hasApiKey(context.getEnvironment());
        if (missing) {
            return ConditionOutcome.match("No OpenAI API key configured");
        }
        return ConditionOutcome.noMatch("OpenAI API key configured");
    }

    static boolean hasApiKey(Environment environment) {
        String apiKey = environment.getProperty(OPENAI_API_KEY_PROPERTY);
        if (!StringUtils.hasText(apiKey)) {
            apiKey = environment.getProperty(OPENAI_API_KEY_ENV);
        }
        return StringUtils.hasText(apiKey);
    }
}
