package com.zhj.learn.aisuperhost.ai.rag;

import com.zhj.learn.aisuperhost.DTO.RecallHitDTO;
import com.zhj.learn.aisuperhost.config.RagConfig;
import com.zhj.learn.aisuperhost.service.RagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
/**
 * 仅负责“召回”阶段的 DocumentRetriever 实现。
 *
 * <p>本类职责：
 * 1) 并行执行关键词召回与向量召回；
 * 2) 将召回命中结果转换成 Spring AI 的 {@link Document}；
 * 3) 为下游 Joiner/重排器补充足够的元数据。
 *
 * <p>本类不处理：
 * - RRF 融合打分；
 * - 模型重排序。
 *
 * <p>上述逻辑统一下放到 {@link MyDocumentJoiner}。
 */
public class MyDocumentRetriever implements DocumentRetriever {

    @Resource
    @Qualifier("ragRecallExecutor")
    private Executor ragRecallExecutor;

    @Resource
    private RagService ragService;

    @Resource
    private RagConfig ragConfig;

    @Override
    public List<Document> retrieve(Query query) {
        // 记录耗时，便于后续排查召回性能瓶颈。
        long startTime = System.currentTimeMillis();

        // 这里使用 QueryTransformer 处理后的查询文本。
        String queryText = query.text();

        // 并行执行两条召回链路：
        // 1) 关键词召回
        // 2) 向量语义召回
        CompletableFuture<List<RecallHitDTO>> keyWordFuture =
                CompletableFuture.supplyAsync(
                        () -> safeKeywordRecall(queryText),
                        ragRecallExecutor);

        CompletableFuture<List<RecallHitDTO>> vectorFuture =
                CompletableFuture.supplyAsync(
                        () -> safeVectorRecall(queryText),
                        ragRecallExecutor);

        // 等待两个异步任务结束并收集结果。
        List<RecallHitDTO> keywordHits = List.of();
        List<RecallHitDTO> vectorHits = List.of();

        try {
            CompletableFuture.allOf(keyWordFuture, vectorFuture).join();
            keywordHits = keyWordFuture.join();
            vectorHits = vectorFuture.join();
        } catch (Exception e) {
            log.error("rag recall failed, query={}", queryText, e);
        }

        // 兜底：两路都为空，说明本次召回没有候选文档。
        if (keywordHits.isEmpty() && vectorHits.isEmpty()) {
            log.warn("recall empty, query={}", queryText);
            return List.of();
        }

        // 本类只做“候选合并”，不做 RRF 与重排。
        List<Document> rawCandidates = mergeAsRawDocuments(keywordHits, vectorHits);

        long cost = System.currentTimeMillis() - startTime;
        log.info("recall done(before join), query={}, keywordHits={}, vectorHits={}, mergedDocs={}, costMs={}",
                queryText, keywordHits.size(), vectorHits.size(), rawCandidates.size(), cost);

        return rawCandidates;
    }

    /**
     * 关键词召回的安全包装。
     * 发生异常时返回空列表，避免中断整条召回链路。
     */
    private List<RecallHitDTO> safeKeywordRecall(String q) {
        try {
            return ragService.keywordRecall(q, ragConfig.getKeywordTopK());
        } catch (Exception e) {
            log.error("keyword recall failed, query={}", q, e);
            return List.of();
        }
    }

    /**
     * 向量召回的安全包装。
     * 发生异常时返回空列表，避免中断整条召回链路。
     */
    private List<RecallHitDTO> safeVectorRecall(String q) {
        try {
            return ragService.vectorRecall(q, ragConfig.getVectorTopK());
        } catch (Exception e) {
            log.error("vector recall failed, query={}", q, e);
            return List.of();
        }
    }

    /**
     * 合并两条召回链路的原始结果，并补充供 Joiner 使用的元数据。
     *
     * <p>补充的关键字段：
     * - route：来源链路（keyword/vector）；
     * - route_rank：在所属链路中的排名（从 0 开始）；
     * - chunk_id/doc_id/score：若存在则透传。
     */
    private List<Document> mergeAsRawDocuments(List<RecallHitDTO> keywordHits, List<RecallHitDTO> vectorHits) {
        List<Document> merged = new ArrayList<>();
        for (int i = 0; i < keywordHits.size(); i++) {
            RecallHitDTO hit = keywordHits.get(i);
            if (hit == null || hit.getContent() == null) {
                continue;
            }
            merged.add(toDocumentWithRouteMetadata(hit, "keyword", i));
        }
        for (int i = 0; i < vectorHits.size(); i++) {
            RecallHitDTO hit = vectorHits.get(i);
            if (hit == null || hit.getContent() == null) {
                continue;
            }
            merged.add(toDocumentWithRouteMetadata(hit, "vector", i));
        }

        // 给 Joiner 预留更大的候选池，便于做 RRF + 模型重排。
        int candidateCap = Math.max(ragConfig.getFinalTopK() * 3, ragConfig.getFinalTopK());
        return merged.stream().limit(candidateCap).toList();
    }

    private Document toDocumentWithRouteMetadata(RecallHitDTO hit, String route, int routeRank) {
        Map<String, Object> metadata = new HashMap<>();
        if (hit.getMetadata() != null) {
            metadata.putAll(hit.getMetadata());
        }
        metadata.put("route", route);
        metadata.put("route_rank", routeRank);
        if (hit.getChunkId() != null) {
            metadata.put("chunk_id", hit.getChunkId());
        }
        if (hit.getDocId() != null) {
            metadata.put("doc_id", hit.getDocId());
        }
        if (hit.getKeywordScore() != null) {
            metadata.put("keyword_score", hit.getKeywordScore());
        }
        if (hit.getVectorScore() != null) {
            metadata.put("vector_score", hit.getVectorScore());
        }
        return new Document(hit.getContent(), metadata);
    }

    private List<Document> trimToTopK(List<Document> docs, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        return docs.stream()
                .limit(topK)
                .toList();
    }
}
