package ch.so.agi.ask.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

//    @Bean
//    @Primary
//    @ConditionalOnMissingBean(ChatModel.class)
//    ChatModel fallbackChatModel() {
//        return new MockChatModel();
//    }

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}

