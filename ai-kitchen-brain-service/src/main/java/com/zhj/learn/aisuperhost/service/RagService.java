package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.DTO.RecallHitDTO;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

@Service
/**
 * RAG 数据访问与召回服务。
 *
 * <p>这个类承担两类职责：
 * 1) 会话记忆读取：从 Redis 读取“窗口对话”和“滚动摘要”；
 * 2) 知识库召回：从 PostgreSQL 执行关键词召回与向量召回。
 *
 * <p>设计选择说明：
 * - 当前阶段为了降低复杂度，把“双路召回”都收敛到一个服务中实现；
 * - 上层（例如 MyDocumentRetriever）只负责编排（并发、融合、兜底、日志）；
 * - 如果后续逻辑膨胀，可再拆分为 KeywordRecallService / VectorRecallService。
 *
 * <p>重要前置条件：
 * - PostgreSQL 侧已安装 pgvector 和中文分词能力（例如 pg_jieba）；
 * - 表结构至少包含：chunk_id/doc_id/content/content_tsv/embedding；
 * - 向量维度必须与 EmbeddingModel 输出一致（否则会插入失败）。
 */
public class RagService {

    /**
     * Redis 记忆服务，用于读取会话级上下文。
     */
    private final RedisMemoryService redisMemoryService;

    /**
     * Spring AI 的向量模型（千问 embedding）。
     * 用于把用户 query 生成向量，供 pgvector 相似度检索。
     */
    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    /**
     * RAG 专用 PostgreSQL 连接配置（与主业务库解耦）。
     */
    @Value("${rag.postgres.jdbc-url}")
    private String ragJdbcUrl;

    @Value("${rag.postgres.username}")
    private String ragJdbcUsername;

    @Value("${rag.postgres.password}")
    private String ragJdbcPassword;

    @Value("${rag.postgres.driver-class-name}")
    private String ragJdbcDriverClassName;

    @Value("${rag.kb.table:kb_chunk}")
    private String ragKbTable;

    @Value("${rag.kb.ts-config:jiebaqry}")
    private String ragTsConfig;

    /**
     * RAG 查询专用 JdbcTemplate。
     * 在 {@link #initRagJdbcTemplate()} 初始化，避免误用主数据源。
     */
    private JdbcTemplate ragJdbcTemplate;


    /**
     * 构造函数，注入 RedisMemoryService。
     */
    public RagService(RedisMemoryService redisMemoryService) {
        this.redisMemoryService = redisMemoryService;
    }

    /**
     * Bean 初始化完成后构建 RAG 专用 JdbcTemplate。
     *
     * <p>为什么不直接注入默认 JdbcTemplate：
     * - 主工程已有 MySQL 主数据源；
     * - RAG 知识库放在 PostgreSQL；
     * - 这里显式构建可避免查错库。
     */
    @PostConstruct
    public void initRagJdbcTemplate() {
        DataSource ds = buildRagDataSource();
        this.ragJdbcTemplate = new JdbcTemplate(ds);
    }

    /**
     * 按配置构建 PostgreSQL 数据源。
     */
    private DataSource buildRagDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(ragJdbcDriverClassName);
        ds.setUrl(ragJdbcUrl);
        ds.setUsername(ragJdbcUsername);
        ds.setPassword(ragJdbcPassword);
        return ds;
    }

    /**
     * 读取短期记忆窗口（最近 N 轮对话）。
     */
    public List<WindowTurn> getWindowTurns(String sessionId) {
        return redisMemoryService.getWindowTurns(sessionId);
    }

    /**
     * 读取滚动摘要（长期记忆压缩结果）。
     */
    public String getSummary(String sessionId) {
        return redisMemoryService.getSummary(sessionId);
    }

    /**
     * 关键词召回（Full-text Search）。
     *
     * <p>实现思路：
     * - 使用 content_tsv @@ plainto_tsquery(...) 进行倒排匹配；
     * - 使用 ts_rank_cd(...) 进行相关性排序；
     * - 返回 topK 结果。
     *
     * <p>注意：
     * - ts-config 必须和建索引时一致（例如 jiebaqry）；
     * - 若不一致，会出现“有数据但检索不到”的现象。
     */
    public List<RecallHitDTO> keywordRecall(String queryText, int topK) {
        if (!StringUtils.hasText(queryText) || topK <= 0) {
            return List.of();
        }
        String sql = """
                SELECT
                    chunk_id,
                    doc_id,
                    content,
                    ts_rank_cd(content_tsv, plainto_tsquery(CAST(? AS regconfig), ?)) AS keyword_score
                FROM %s
                WHERE content_tsv @@ plainto_tsquery(CAST(? AS regconfig), ?)
                ORDER BY keyword_score DESC
                LIMIT ?
                """.formatted(ragKbTable);

        return ragJdbcTemplate.query(sql, (rs, rowNum) -> mapKeywordHit(rs),
                ragTsConfig, queryText, ragTsConfig, queryText, topK);
    }

    /**
     * 向量语义召回（pgvector）。
     *
     * <p>实现思路：
     * - 先对 queryText 做 embedding；
     * - 使用 embedding <=> queryVector 计算距离并排序；
     * - 返回 topK 结果。
     *
     * <p>评分说明：
     * - SQL 中额外返回了 1 - distance 作为 vector_score，
     *   仅用于后续融合或日志观察；
     * - 真正排序依据仍是 <=> 距离。
     */
    public List<RecallHitDTO> vectorRecall(String queryText, int topK) {
        if (!StringUtils.hasText(queryText) || topK <= 0) {
            return List.of();
        }
        float[] queryVector = qwenEmbeddingModel.embed(queryText);
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        String sql = """
                SELECT
                    chunk_id,
                    doc_id,
                    content,
                    1 - (embedding <=> CAST(? AS vector)) AS vector_score
                FROM %s
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """.formatted(ragKbTable);

        String vectorLiteral = toVectorLiteral(queryVector);
        return ragJdbcTemplate.query(sql, (rs, rowNum) -> mapVectorHit(rs), vectorLiteral, vectorLiteral, topK);
    }

    /**
     * 把关键词召回结果映射为统一 DTO。
     */
    private RecallHitDTO mapKeywordHit(ResultSet rs) throws java.sql.SQLException {
        return RecallHitDTO.builder()
                .chunkId(rs.getString("chunk_id"))
                .docId(rs.getString("doc_id"))
                .content(rs.getString("content"))
                .metadata(Map.of("route", "keyword"))
                .keywordScore(rs.getDouble("keyword_score"))
                .route("keyword")
                .build();
    }

    /**
     * 把向量召回结果映射为统一 DTO。
     */
    private RecallHitDTO mapVectorHit(ResultSet rs) throws java.sql.SQLException {
        return RecallHitDTO.builder()
                .chunkId(rs.getString("chunk_id"))
                .docId(rs.getString("doc_id"))
                .content(rs.getString("content"))
                .metadata(Map.of("route", "vector"))
                .vectorScore(rs.getDouble("vector_score"))
                .route("vector")
                .build();
    }

    /**
     * 将 float[] 向量转成 pgvector 可识别的文本格式：
     * [0.12,-0.43,...]
     */
    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
