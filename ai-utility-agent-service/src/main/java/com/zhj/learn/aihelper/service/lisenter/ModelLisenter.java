package com.zhj.learn.aihelper.service.lisenter;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelLisenter {

    @Bean
    ChatModelListener chatModelListener(){
        return new ChatModelListener() {

            private static final Logger LOG= LoggerFactory.getLogger(ChatModelListener.class);

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                LOG.info("onResponse:{}",responseContext.chatResponse());
            }

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                LOG.info("onRequest:{}",requestContext.chatRequest());
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                LOG.info("onError:{}",errorContext.error().getMessage());
            }
        };
    }

}
