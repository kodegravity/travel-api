package com.aitravel.companion.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AiConfigTest {

    @Test
    void refusesToBuildChatClientWithoutOpenAiKey() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);

        assertThatThrownBy(() -> new AiConfig().chatClient(builder, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
