package com.huah.ai.platform.gateway.service;

import com.huah.ai.platform.common.exception.AiServiceException;
import com.huah.ai.platform.gateway.config.ModelRegistryConfig.ModelDefinition;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

final class GatewayModelClientFactory {

    ChatModel buildChatModel(ModelDefinition definition) {
        return switch (definition.getProvider().toLowerCase()) {
            case "openai", "deepseek", "qwen", "zhipu", "moonshot" -> buildOpenAiCompatibleModel(definition);
            case "anthropic" -> buildAnthropicModel(definition);
            case "ollama" -> buildOllamaModel(definition);
            default -> throw new AiServiceException("Unsupported model provider: " + definition.getProvider());
        };
    }

    private ChatModel buildOpenAiCompatibleModel(ModelDefinition definition) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(definition.getBaseUrl())
                .apiKey(definition.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildAnthropicModel(ModelDefinition definition) {
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(definition.getBaseUrl() != null && !definition.getBaseUrl().isBlank()
                        ? definition.getBaseUrl()
                        : AnthropicApi.DEFAULT_BASE_URL)
                .apiKey(definition.getApiKey())
                .build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .maxTokens(4096)
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel buildOllamaModel(ModelDefinition definition) {
        OllamaApi api = OllamaApi.builder()
                .baseUrl(definition.getBaseUrl())
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(definition.getName())
                .temperature(0.7)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }
}
