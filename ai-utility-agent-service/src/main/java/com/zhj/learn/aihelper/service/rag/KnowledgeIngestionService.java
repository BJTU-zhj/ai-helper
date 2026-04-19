package com.zhj.learn.aihelper.service.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
/**
 * DOCX 知识库离线入库服务（Ingestion Pipeline）。
 *
 * <p>职责边界：
 * 1) 读取指定目录的 .docx 文件；
 * 2) 抽取纯文本并切分为可检索的 chunk；
 * 3) 计算每个 chunk 的向量；
 * 4) 将文档、chunk、任务进度写入 PostgreSQL。
 *
 * <p>设计说明：
 * - 这是“离线/准离线”流程，不建议放入在线问答主链路。
 * - 当前实现追求可读性与可演进性：先保证链路打通，再逐步优化并发、重试、幂等策略。
 * - 对同一 docId 的 chunk 采取“先删后写”，简化版本切换逻辑，便于学习阶段排错。
 */
public class KnowledgeIngestionService {

    @Value("${app.rag.docs-path:classpath:rag}")
    private String docsPath;

    @Value("${app.rag.max-segment-size:800}")
    private int maxSegmentSize;

    @Value("${app.rag.max-overlap-size:120}")
    private int maxOverlapSize;

    @Value("${app.rag.embedding-batch-size:10}")
    private int embeddingBatchSize;

    @Value("${app.rag.embedding-dimension:1024}")
    private int embeddingDimension;

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    @Resource
    private ResourceLoader resourceLoader;

    @Resource
    @Qualifier("ragJdbcTemplate")
    private JdbcTemplate ragJdbcTemplate;

    @Resource
    private RagIngestionTaskService ragIngestionTaskService;

    /**
     * 扫描 docsPath 下全部 docx，并执行“任务级”入库。
     *
     * <p>任务状态机：
     * - RUNNING: 任务创建后和处理中；
     * - SUCCESS: 全部成功；
     * - PARTIAL_SUCCESS: 部分失败；
     * - FAILED: 任务级异常（例如目录读取失败）。
     *
     * <p>事务说明：
     * - 本方法标注了事务，但包含了多个文档循环与远程 embedding 调用。
     * - 生产环境通常会拆成“每文档独立事务”，避免长事务持锁过久。
     */
    @Transactional(rollbackFor = Exception.class)
    public String ingestAllDocx() {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime startedAt = OffsetDateTime.now();
        safeCreateTask(taskId, startedAt);

        int total = 0;
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();

        try {
            org.springframework.core.io.Resource[] resources = resolveDocxResources();
            total = resources.length;
            safeUpdateTaskProgress(taskId, "RUNNING", total, success, fail, null, null);

            for (org.springframework.core.io.Resource resource : resources) {
                try {
                    ingestSingleResource(resource);
                    success++;
                } catch (Exception ex) {
                    fail++;
                    errors.add(resource.getFilename() + ":" + ex.getMessage());
                    log.error("ingest doc failed. file={}", resource.getFilename(), ex);
                }
                safeUpdateTaskProgress(taskId, "RUNNING", total, success, fail, null, null);
            }

            String err = errors.isEmpty() ? null : String.join(" | ", errors);
            safeUpdateTaskProgress(taskId, fail == 0 ? "SUCCESS" : "PARTIAL_SUCCESS", total, success, fail, err, OffsetDateTime.now());
            return "taskId=" + taskId + ", total=" + total + ", success=" + success + ", fail=" + fail;
        } catch (Exception ex) {
            safeUpdateTaskProgress(taskId, "FAILED", total, success, fail, ex.getMessage(), OffsetDateTime.now());
            throw new RuntimeException("RAG ingestion failed. taskId=" + taskId, ex);
        }
    }

    /**
     * 解析文档资源列表。
     *
     * <p>支持两种路径：
     * 1) classpath:xxx  适合项目资源目录；
     * 2) 文件系统路径    适合外部知识库目录。
     *
     * <p>注意：classpath* 通配在 fat-jar 场景可用，但大规模目录建议改为对象存储或挂载目录。
     */
    private org.springframework.core.io.Resource[] resolveDocxResources() throws Exception {
        if (docsPath.startsWith("classpath:")) {
            String normalized = docsPath.substring("classpath:".length());
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            return resolver.getResources("classpath*:" + normalized + "/*.docx");
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        return resolver.getResources("file:" + docsPath + "/*.docx");
    }

    /**
     * 处理单个文档的完整链路。
     *
     * <p>步骤：
     * 1) 生成文档标识（docId）与摘要信息（hash/path/title）；
     * 2) upsert 文档主表；
     * 3) 文本切块；
     * 4) 删除旧 chunk（当前版本策略）；
     * 5) 分批 embedding 并写入 chunk 表。
     *
     * <p>docId 生成策略：
     * - 基于文件名 hash，便于稳定映射同一文档。
     * - 如存在同名不同内容场景，可改为“业务主键 + 版本号”策略。
     */
    private void ingestSingleResource(org.springframework.core.io.Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalStateException("resource filename is empty");
        }

        String title = filename.endsWith(".docx") ? filename.substring(0, filename.length() - 5) : filename;
        String docId = "DOC_" + sha256Hex(filename).substring(0, 16);
        String sourcePath = resource.getURI().toString();
        List<String> paragraphs = extractDocxParagraphs(resource);
        String content = String.join("\n", paragraphs);
        String contentHash = sha256Hex(content);

        upsertDoc(docId, title, sourcePath, contentHash);

        List<TextSegment> segments = buildSegmentsFromParagraphs(docId, title, paragraphs);
        if (segments.isEmpty()) {
            return;
        }

        ragJdbcTemplate.update("DELETE FROM kb_chunk WHERE doc_id = ?", docId);

        int batch = Math.max(1, Math.min(embeddingBatchSize, 10));
        int chunkNo = 1;
        for (int i = 0; i < segments.size(); i += batch) {
            List<TextSegment> oneBatch = segments.subList(i, Math.min(i + batch, segments.size()));
            List<Embedding> embeddings = qwenEmbeddingModel.embedAll(oneBatch).content();
            for (int j = 0; j < oneBatch.size(); j++) {
                TextSegment seg = oneBatch.get(j);
                Embedding emb = embeddings.get(j);
                saveChunk(docId, chunkNo, seg.text(), emb);
                chunkNo++;
            }
        }
        log.info("ingest one doc done. docId={}, title={}, chunks={}", docId, title, segments.size());
    }

    /**
     * 文档主表幂等写入。
     *
     * <p>ON CONFLICT(doc_id) 更新关键字段，确保重复执行同一文档不会产生脏数据。
     * 当前 metadata 先写空 JSON，对后续存储标签/作者/来源系统预留扩展位。
     */
    private void upsertDoc(String docId, String title, String sourcePath, String contentHash) {
        String sql = """
                INSERT INTO kb_doc (doc_id, title, source_path, source_type, version, content_hash, status, metadata)
                VALUES (?, ?, ?, 'local_file', 'v1', ?, 1, CAST(? AS jsonb))
                ON CONFLICT (doc_id) DO UPDATE SET
                    title = EXCLUDED.title,
                    source_path = EXCLUDED.source_path,
                    content_hash = EXCLUDED.content_hash,
                    updated_at = NOW()
                """;
        ragJdbcTemplate.update(sql, docId, title, sourcePath, contentHash, "{}");
    }

    /**
     * 保存单个 chunk（包含向量）。
     *
     * <p>vector 字段通过 CAST(? AS vector) 写入 pgvector。
     * 这里将 float[] 序列化为字符串 "[v1,v2,...]"，是最直接且易调试的写法。
     */
    private void saveChunk(String docId, int chunkNo, String content, Embedding embedding) {
        String chunkId = docId + "_" + chunkNo;
        validateEmbeddingDimension(embedding);
        String vectorLiteral = toVectorLiteral(embedding.vector());
        String sql = """
                INSERT INTO kb_chunk (chunk_id, doc_id, chunk_no, content, embedding, metadata)
                VALUES (?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb))
                ON CONFLICT (chunk_id) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW()
                """;
        ragJdbcTemplate.update(sql, chunkId, docId, chunkNo, content, vectorLiteral, "{}");
    }

    private void validateEmbeddingDimension(Embedding embedding) {
        if (embedding == null || embedding.vector() == null) {
            throw new IllegalStateException("embedding is null");
        }
        int actual = embedding.vector().length;
        if (actual != embeddingDimension) {
            throw new IllegalStateException(
                    "embedding dimension mismatch, expected=" + embeddingDimension + ", actual=" + actual
                            + ". Please align db column vector(N) and app.rag.embedding-dimension."
            );
        }
    }

    private void safeCreateTask(String taskId, OffsetDateTime startedAt) {
        try {
            ragIngestionTaskService.createTask(taskId, startedAt);
        } catch (Exception ex) {
            log.error("create ingest task failed. taskId={}", taskId, ex);
        }
    }

    private void safeUpdateTaskProgress(String taskId, String status, int total, int success, int fail, String error, OffsetDateTime finishedAt) {
        try {
            ragIngestionTaskService.updateTaskProgress(taskId, status, total, success, fail, error, finishedAt);
        } catch (Exception ex) {
            log.error("update ingest task failed. taskId={}, status={}", taskId, status, ex);
        }
    }

    /**
     * 从 docx 提取段落列表。
     *
     * <p>与“先拼成大字符串再切分”的方案相比，这里直接保留段落边界，
     * 后续切块会更稳定，也便于扩展段落级 metadata（段号、标题层级等）。
     */
    private List<String> extractDocxParagraphs(org.springframework.core.io.Resource resource) throws Exception {
        try (InputStream inputStream = resource.getInputStream(); XWPFDocument xwpfDocument = new XWPFDocument(inputStream)) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : xwpfDocument.getParagraphs()) {
                if (paragraph == null || !StringUtils.hasText(paragraph.getText())) {
                    continue;
                }
                paragraphs.add(paragraph.getText().trim());
            }
            return paragraphs;
        }
    }

    /**
     * 段落优先切块：
     * 1) 尽量按段落追加，直到达到 maxSegmentSize；
     * 2) 超限时切出一个 chunk；
     * 3) 用 maxOverlapSize 控制“字符级重叠”以保留上下文连续性。
     */
    private List<TextSegment> buildSegmentsFromParagraphs(String docId, String title, List<String> paragraphs) {
        List<TextSegment> segments = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return segments;
        }

        int chunkNo = 1;
        StringBuilder current = new StringBuilder(Math.max(1024, maxSegmentSize + maxOverlapSize));

        for (String paragraph : paragraphs) {
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }
            if (paragraph.length() > maxSegmentSize) {
                if (current.length() > 0) {
                    segments.add(toSegment(docId, title, chunkNo++, current.toString()));
                    current = new StringBuilder(overlapTail(current.toString(), maxOverlapSize));
                }
                int start = 0;
                while (start < paragraph.length()) {
                    int end = Math.min(start + maxSegmentSize, paragraph.length());
                    String piece = paragraph.substring(start, end);
                    segments.add(toSegment(docId, title, chunkNo++, piece));
                    if (end >= paragraph.length()) {
                        break;
                    }
                    start = Math.max(end - maxOverlapSize, start + 1);
                }
                continue;
            }

            int appendLength = paragraph.length() + (current.length() == 0 ? 0 : 1);
            if (current.length() + appendLength > maxSegmentSize) {
                segments.add(toSegment(docId, title, chunkNo++, current.toString()));
                current = new StringBuilder(overlapTail(current.toString(), maxOverlapSize));
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(paragraph);
        }

        if (current.length() > 0) {
            segments.add(toSegment(docId, title, chunkNo, current.toString()));
        }
        return segments;
    }

    private TextSegment toSegment(String docId, String title, int chunkNo, String text) {
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        metadataMap.put("doc_id", docId);
        metadataMap.put("title", title);
        metadataMap.put("chunk_no", chunkNo);
        return TextSegment.from(text, dev.langchain4j.data.document.Metadata.from(metadataMap));
    }

    private String overlapTail(String text, int overlapSize) {
        if (!StringUtils.hasText(text) || overlapSize <= 0) {
            return "";
        }
        if (text.length() <= overlapSize) {
            return text;
        }
        return text.substring(text.length() - overlapSize);
    }

    /**
     * 计算 SHA-256（用于内容去重/变更识别/稳定 ID 构造）。
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha256 failed", e);
        }
    }

    /**
     * 将 embedding 向量转为 pgvector 可识别的文本格式："[1.0,2.0,...]"。
     */
    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
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
