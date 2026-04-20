package com.zhj.learn.aisuperhost.ai.rag;

import com.zhj.learn.aisuperhost.config.RagConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
/**
 * 自定义 DocumentJoiner：
 * 1) 对多路候选做 RRF（Reciprocal Rank Fusion）融合打分；
 * 2) 调用 DashScope Rerank 接口做语义重排序；
 * 3) 返回最终 topK 文档供后续提示词拼装。
 *
 * <p>输入约定：
 * - key：QueryTransformer 处理后的查询对象；
 * - value：来自召回阶段的候选文档列表（可能是一条或多条链路）。
 *
 * <p>输出约定：
 * - 经过 RRF + 模型重排后的有序 {@link Document} 列表。
 */
public class MyDocumentJoiner implements DocumentJoiner {

    @Resource
    private RagConfig ragConfig;

    @Resource
    private RestClient.Builder restClientBuilder;

    @Value("${spring.ai.model.qwen.rerank.api-key}")
    private String qwenRerankApiKey;

    @Value("${spring.ai.model.qwen.rerank.model-name:qwen3-vl-rerank}")
    private String qwenRerankModelName;

    @Value("${spring.ai.model.qwen.rerank.endpoint:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String qwenRerankEndpoint;

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        if (documentsForQuery == null || documentsForQuery.isEmpty()) {
            return List.of();
        }

        // Spring AI 当前 joiner 接口最终返回一条 Document 列表，这里取第一组 Query 输入处理。
        Map.Entry<Query, List<List<Document>>> first = documentsForQuery.entrySet().iterator().next();
        Query query = first.getKey();
        List<List<Document>> candidatesFromRoutes = first.getValue();
        String queryText = query == null ? "" : query.text();

        if (candidatesFromRoutes == null || candidatesFromRoutes.isEmpty()) {
            return List.of();
        }

        long start = System.currentTimeMillis();

        // 路由归一化：
        // - 若上游已按多路列表传入，直接使用；
        // - 若只有一条合并列表，则尝试按 metadata.route 再拆分。
        List<List<Document>> normalizedRoutes = normalizeRouteLists(candidatesFromRoutes);

        List<ScoredDocument> rrfRanked = rrfFuse(normalizedRoutes, ragConfig.getRrfK());
        if (rrfRanked.isEmpty()) {
            return List.of();
        }

        int rerankTopN = Math.max(ragConfig.getFinalTopK() * 3, ragConfig.getFinalTopK());
        List<ScoredDocument> candidatePool = rrfRanked.stream().limit(rerankTopN).toList();

        // 模型重排分数范围约束在 [0,1]。
        List<ScoredDocument> reranked = applyModelRerank(queryText, candidatePool);

        List<Document> result = reranked.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::finalScore).reversed())
                .limit(ragConfig.getFinalTopK())
                .map(ScoredDocument::toDocument)
                .toList();

        long cost = System.currentTimeMillis() - start;
        log.info("join done, query={}, routeCount={}, inputDocs={}, rrfDocs={}, outputDocs={}, costMs={}",
                queryText, normalizedRoutes.size(), countAll(normalizedRoutes), rrfRanked.size(), result.size(), cost);
        return result;
    }

    /**
     * 以“稳健模式”构建路由列表。
     * 当上游只给一条合并列表时，尝试按 metadata.route 重新分组。
     */
    private List<List<Document>> normalizeRouteLists(List<List<Document>> rawRoutes) {
        if (rawRoutes.size() > 1) {
            return rawRoutes;
        }
        List<Document> merged = rawRoutes.get(0);
        if (merged == null || merged.isEmpty()) {
            return List.of();
        }

        Map<String, List<Document>> byRoute = new LinkedHashMap<>();
        for (Document d : merged) {
            String route = metadataString(d, "route");
            if (route == null || route.isBlank()) {
                route = "default";
            }
            byRoute.computeIfAbsent(route, k -> new ArrayList<>()).add(d);
        }
        return new ArrayList<>(byRoute.values());
    }

    /**
     * RRF 打分公式：Σ 1 / (k + rank + 1)
     *
     * <p>去重键策略：
     * - 优先使用 metadata.chunk_id；
     * - 若不存在则回退为文本 hash。
     */
    private List<ScoredDocument> rrfFuse(List<List<Document>> routeLists, int k) {
        Map<String, ScoredDocument> scoreMap = new HashMap<>();
        int safeK = Math.max(k, 1);

        for (List<Document> route : routeLists) {
            if (route == null || route.isEmpty()) {
                continue;
            }
            for (int rank = 0; rank < route.size(); rank++) {
                Document doc = route.get(rank);
                if (doc == null) {
                    continue;
                }
                String key = dedupKey(doc);
                double add = 1.0D / (safeK + rank + 1);
                scoreMap.computeIfAbsent(key, x -> new ScoredDocument(doc)).addRrf(add);
            }
        }

        return scoreMap.values().stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::rrfScore).reversed())
                .toList();
    }

    /**
     * 模型重排步骤：
     * - 调用 qwen3-vl-rerank 获取相关性分；
     * - 解析模型输出的分值；
     * - 与 RRF 分数做加权融合得到最终分。
     */
    private List<ScoredDocument> applyModelRerank(String queryText, List<ScoredDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        double wRrf = 0.35D;
        double wModel = 0.65D;

        List<String> documentTexts = docs.stream()
                .map(sd -> safeText(sd.document.getText()))
                .toList();

        Map<Integer, Double> rerankScores = batchRerankScores(queryText, documentTexts);

        for (int i = 0; i < docs.size(); i++) {
            ScoredDocument sd = docs.get(i);
            double modelScore = rerankScores.getOrDefault(i, 0.0D);
            sd.setModelScore(modelScore);
            sd.setFinalScore(wRrf * sd.rrfScore() + wModel * modelScore);
        }
        return docs;
    }

    /**
     * 调用 DashScope Rerank 专用接口进行批量重排打分。
     * 返回值：key=候选文档在输入列表中的索引，value=相关性分数（0~1）。
     */
    private Map<Integer, Double> batchRerankScores(String queryText, List<String> documents) {
        Map<Integer, Double> scores = new HashMap<>();
        if (documents == null || documents.isEmpty()) {
            return scores;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", qwenRerankModelName);

            Map<String, Object> input = new HashMap<>();
            input.put("query", safeText(queryText));
            input.put("documents", documents);
            payload.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", documents.size());
            payload.put("parameters", parameters);

            RestClient restClient = restClientBuilder.build();
            Map<String, Object> response = restClient.post()
                    .uri(qwenRerankEndpoint)
                    .header("Authorization", "Bearer " + qwenRerankApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            parseRerankResponse(scores, response);
        } catch (Exception e) {
            log.error("rerank model scoring failed", e);
        }

        return scores;
    }

    @SuppressWarnings("unchecked")
    private void parseRerankResponse(Map<Integer, Double> scores, Map<String, Object> response) {
        if (response == null) {
            return;
        }
        Object outputObj = response.get("output");
        if (!(outputObj instanceof Map<?, ?> outputMap)) {
            return;
        }
        Object resultsObj = outputMap.get("results");
        if (!(resultsObj instanceof List<?> results)) {
            return;
        }
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> resultMap)) {
                continue;
            }
            Object indexObj = resultMap.get("index");
            Object scoreObj = resultMap.get("relevance_score");
            if (indexObj instanceof Number indexNum && scoreObj instanceof Number scoreNum) {
                scores.put(indexNum.intValue(), scoreNum.doubleValue());
            }
        }
    }

    private String dedupKey(Document d) {
        String chunkId = metadataString(d, "chunk_id");
        if (chunkId != null && !chunkId.isBlank()) {
            return chunkId;
        }
        return Integer.toHexString(safeText(d.getText()).hashCode());
    }

    private String metadataString(Document d, String key) {
        if (d == null || d.getMetadata() == null) {
            return null;
        }
        Object value = d.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int countAll(List<List<Document>> routes) {
        int total = 0;
        for (List<Document> route : routes) {
            if (route != null) {
                total += route.size();
            }
        }
        return total;
    }

    private String safeText(String t) {
        return t == null ? "" : t.trim();
    }

    /**
     * 候选文档分数载体（可变对象）。
     * 用于在 join 阶段累积 RRF 分、模型分和最终分。
     */
    private static class ScoredDocument {
        private final Document document;
        private double rrfScore;
        private double modelScore;
        private double finalScore;

        private ScoredDocument(Document document) {
            this.document = document;
        }

        private void addRrf(double add) {
            this.rrfScore += add;
        }

        private double rrfScore() {
            return rrfScore;
        }

        private double finalScore() {
            return finalScore;
        }

        private void setModelScore(double modelScore) {
            this.modelScore = modelScore;
        }

        private void setFinalScore(double finalScore) {
            this.finalScore = finalScore;
        }

        private Document toDocument() {
            Map<String, Object> newMeta = new HashMap<>();
            if (document.getMetadata() != null) {
                newMeta.putAll(document.getMetadata());
            }
            newMeta.put("rrf_score", rrfScore);
            newMeta.put("rerank_model_score", modelScore);
            newMeta.put("final_score", finalScore);
            return new Document(document.getText(), newMeta);
        }
    }
}
