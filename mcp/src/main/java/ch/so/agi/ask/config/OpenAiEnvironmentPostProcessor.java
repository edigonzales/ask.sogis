package ch.so.agi.ask.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;

/**
 * Passt die Spring-AI-Modellauswahl automatisch an vorhandene Credentials an.
 * Ohne OpenAI-Key werden alle relevanten Modelle auf {@code mock} gesetzt, mit
 * Key wieder auf {@code openai} (sofern nicht explizit anders konfiguriert).
 */
public class OpenAiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEnvironmentPostProcessor.class);

    private static final String PROPERTY_SOURCE_NAME = "ask.sogis#model-overrides";

    private static final String OPENAI_API_KEY_PROPERTY = "spring.ai.openai.api-key";

    private static final List<String> MODEL_PROPERTIES = List.of(
            SpringAIModelProperties.CHAT_MODEL,
            SpringAIModelProperties.EMBEDDING_MODEL,
            SpringAIModelProperties.TEXT_EMBEDDING_MODEL,
            SpringAIModelProperties.MULTI_MODAL_EMBEDDING_MODEL,
            SpringAIModelProperties.IMAGE_MODEL,
            SpringAIModelProperties.AUDIO_TRANSCRIPTION_MODEL,
            SpringAIModelProperties.AUDIO_SPEECH_MODEL,
            SpringAIModelProperties.MODERATION_MODEL
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.out.println("**** 1");
        boolean apiKeyPresent = StringUtils.hasText(environment.getProperty(OPENAI_API_KEY_PROPERTY));
        Map<String, String> overrides = new HashMap<>();

        if (apiKeyPresent) {
            for (String property : MODEL_PROPERTIES) {
                String configured = environment.getProperty(property);
                if (!StringUtils.hasText(configured) || "mock".equalsIgnoreCase(configured)) {
                    overrides.put(property, SpringAIModels.OPENAI);
                }
            }
            if (!overrides.isEmpty()) {
                log.info("OpenAI API key detected – enabling OpenAI model auto-configuration");
                applyOverrides(environment, overrides);
            }
            return;
        }

        for (String property : MODEL_PROPERTIES) {
            String configured = environment.getProperty(property);
            if (!StringUtils.hasText(configured) || SpringAIModels.OPENAI.equalsIgnoreCase(configured)) {
                overrides.put(property, "mock");
            }
        }

        if (!overrides.isEmpty()) {
            log.warn("No OpenAI API key configured – switching Spring AI models to mock implementations");
            applyOverrides(environment, overrides);
        }
    }

    private void applyOverrides(ConfigurableEnvironment environment, Map<String, String> overrides) {
        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        Map<String, Object> values = new HashMap<>(overrides);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
