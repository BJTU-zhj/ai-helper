package com.zhj.learn.aisuperhost.config;

import com.zhj.learn.aisuperhost.ai.rag.MyDocumentJoiner;
import com.zhj.learn.aisuperhost.ai.rag.MyDocumentRetriever;
import com.zhj.learn.aisuperhost.ai.rag.MyQueryTransformer;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 顾问装配配置。
 *
 * <p>职责：
 * 1) 组装检索前（QueryTransformer）；
 * 2) 组装检索中（DocumentRetriever）；
 * 3) 组装检索后（DocumentJoiner/Rerank）；
 * 4) 暴露统一的 RetrievalAugmentationAdvisor 供 ChatClient 使用。
 */
@Configuration
public class RagAdvisorConfig {

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            MyQueryTransformer myQueryTransformer,
            MyDocumentRetriever myDocumentRetriever,
            MyDocumentJoiner myDocumentJoiner) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(myQueryTransformer)
                .documentRetriever(myDocumentRetriever)
                .documentJoiner(myDocumentJoiner)
                // 放在记忆加载后、记忆持久化前执行，保证检索拿到完整上下文。
                .order(150)
                .build();
    }
}
