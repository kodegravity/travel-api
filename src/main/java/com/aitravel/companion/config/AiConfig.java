package com.aitravel.companion.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the ChatClient only when {@code travel.ai.enabled=true}, so the app runs without an OpenAI key by default. */
@Configuration
@EnableConfigurationProperties(TravelAiProperties.class)
@ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, @Value("${spring.ai.openai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "travel.ai.enabled=true requires the OPENAI_API_KEY environment variable to be set. "
                            + "Set it, or run with TRAVEL_AI_ENABLED=false.");
        }
        return builder.build();
    }
}
