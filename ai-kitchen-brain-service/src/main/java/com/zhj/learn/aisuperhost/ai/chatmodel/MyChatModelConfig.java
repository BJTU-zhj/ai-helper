package com.zhj.learn.aisuperhost.ai.chatmodel;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MyChatModelConfig {

    //千问
    @Value("${spring.ai.model.qwen.base-url}")
    private String qwenBaseUrl;

    @Value("${spring.ai.model.qwen.chat.api-key}")
    private String qwenChatApiKey;

    @Value("${spring.ai.model.qwen.chat.model-name}")
    private String qwenChatModelName;

    @Value("${spring.ai.model.qwen.embedding.api-key}")
    private String qwenEmbeddingApiKey;

    @Value("${spring.ai.model.qwen.embedding.model-name}")
    private String qwenEmbeddingModelName;

    @Value("${spring.ai.model.qwen.rerank.api-key}")
    private String qwenRerankApiKey;

    @Value("${spring.ai.model.qwen.rerank.model-name}")
    private String qwenRerankModelName;

    //deepseek
    @Value("${spring.ai.model.deepseek.base-url}")
    private String deepSeekBaseUrl;

    @Value("${spring.ai.model.deepseek.chat.api-key}")
    private String deepSeekChatApiKey;

    @Value("${spring.ai.model.deepseek.chat.model-name}")
    private String deepSeekModelName;


    //创建千问对话模型
    @Bean
    @Primary
    public ChatModel qwenChatModel() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(qwenChatApiKey)
                .baseUrl(qwenBaseUrl)
                .build();

        OpenAiChatOptions options=OpenAiChatOptions.builder()
                .model(qwenChatModelName)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }


    //创建千问向量模型
    @Bean
    @Primary
    public EmbeddingModel qwenEmbeddingModel() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(qwenEmbeddingApiKey)
                .baseUrl(qwenBaseUrl)
                .build();

        OpenAiEmbeddingOptions options=OpenAiEmbeddingOptions.builder()
                .model(qwenEmbeddingModelName)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, options);
    }

    //创建千问重排模型（作为打分模型调用）
    @Bean(name = "qwenRerankChatModel")
    public ChatModel qwenRerankChatModel() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(qwenRerankApiKey)
                .baseUrl(qwenBaseUrl)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(qwenRerankModelName)
                .temperature(0.0)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }


    //创建deepseek对话模型
    @Bean
    public ChatModel deepSeekChatModel() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(deepSeekChatApiKey)
                .baseUrl(deepSeekBaseUrl)
                .build();

        OpenAiChatOptions options=OpenAiChatOptions.builder()
                .model(deepSeekModelName)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }


}
