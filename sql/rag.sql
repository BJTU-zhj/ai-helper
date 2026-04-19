-- =========================================================
-- RAG 数据库初始化脚本（PostgreSQL + pgvector + pg_jieba）
-- ---------------------------------------------------------
-- 目标：
-- 1) 建立“文档主表 + 分块检索表 + 入库任务表”的标准三层结构；
-- 2) 支持关键词检索（tsvector）与向量检索（pgvector）并行；
-- 3) 通过触发器自动维护 updated_at 与 content_tsv；
-- 4) 为后续 RRF 融合召回提供结构基础。
--
-- 分词配置说明：
-- - 全文检索统一使用 jiebaqry，保证“建索引”和“查询”分词配置一致。
-- - 若后续改为 jiebacfg，需同步修改触发器和业务检索 SQL。
-- =========================================================

-- 扩展依赖：
-- - pg_jieba: 中文分词
-- - vector:   向量字段与近邻检索能力
CREATE EXTENSION IF NOT EXISTS pg_jieba;
CREATE EXTENSION IF NOT EXISTS vector;

-- 先删除旧对象，便于在本地重复执行。
-- 注意：生产环境请改为版本化迁移（Flyway/Liquibase），不要直接 DROP。
DROP TABLE IF EXISTS kb_chunk CASCADE;
DROP TABLE IF EXISTS kb_doc CASCADE;
DROP TABLE IF EXISTS kb_ingest_task CASCADE;

-- =========================================================
-- 1) 文档主表：kb_doc
-- ---------------------------------------------------------
-- 一行代表一个“源文档”（例如一个 docx 文件）。
-- 主要用于：
-- - 文档级元数据管理（标题、来源、版本、状态）；
-- - 文档级去重（content_hash）；
-- - 与 chunk 表进行 1:N 关联。
-- =========================================================
CREATE TABLE kb_doc (
    id              BIGSERIAL PRIMARY KEY,              -- 数据库内部主键（自增）
    doc_id          VARCHAR(128) NOT NULL UNIQUE,       -- 业务文档ID（建议稳定且可追溯）
    title           TEXT NOT NULL,                      -- 文档标题（用于展示/粗筛）
    source_path     TEXT,                               -- 原始来源路径（文件路径/URL/对象存储key）
    source_type     VARCHAR(32) DEFAULT 'local_file',   -- 来源类型（local_file/http/oss 等）
    version         VARCHAR(32) DEFAULT 'v1',           -- 文档版本号（支持灰度切换）
    content_hash    VARCHAR(64) NOT NULL,               -- 文档内容哈希（变更检测/去重）
    status          SMALLINT DEFAULT 1,                 -- 状态位：1有效、0停用
    metadata        JSONB DEFAULT '{}'::jsonb,          -- 扩展元数据（标签、作者、业务域等）
    created_at      TIMESTAMPTZ DEFAULT NOW(),          -- 创建时间
    updated_at      TIMESTAMPTZ DEFAULT NOW()           -- 更新时间（触发器维护）
);

-- =========================================================
-- 2) 分块表：kb_chunk
-- ---------------------------------------------------------
-- 一行代表文档中的一个 chunk（语义分片）。
-- 检索的主战场：关键词召回 + 向量召回都在 chunk 粒度完成。
-- =========================================================
CREATE TABLE kb_chunk (
    id              BIGSERIAL PRIMARY KEY,              -- 数据库内部主键
    chunk_id        VARCHAR(160) NOT NULL UNIQUE,       -- 业务chunk ID（如 DOC_xxx_12）
    doc_id          VARCHAR(128) NOT NULL,              -- 所属文档ID（关联 kb_doc.doc_id）
    chunk_no        INTEGER NOT NULL,                   -- chunk 顺序号（文档内）
    content         TEXT NOT NULL,                      -- chunk 文本
    content_tsv     TSVECTOR,                           -- 全文检索向量（触发器自动生成）
    embedding       vector(1024),                       -- 向量字段（text-embedding-v4 当前为 1024 维）
    metadata        JSONB DEFAULT '{}'::jsonb,          -- 扩展字段（章节、页码、标签等）
    created_at      TIMESTAMPTZ DEFAULT NOW(),          -- 创建时间
    updated_at      TIMESTAMPTZ DEFAULT NOW(),          -- 更新时间（触发器维护）
    CONSTRAINT fk_kb_chunk_doc
        FOREIGN KEY (doc_id) REFERENCES kb_doc(doc_id) ON DELETE CASCADE, -- 删文档自动删chunk
    CONSTRAINT uk_kb_chunk_doc_no
        UNIQUE (doc_id, chunk_no)                       -- 保证同文档内 chunk_no 唯一
);

-- =========================================================
-- 3) 入库任务表：kb_ingest_task
-- ---------------------------------------------------------
-- 记录每次知识库导入任务的执行情况。
-- 用于运维排障、审计追踪和后续指标统计（成功率、失败率、耗时）。
-- =========================================================
CREATE TABLE kb_ingest_task (
    id                  BIGSERIAL PRIMARY KEY,          -- 数据库内部主键
    task_id             VARCHAR(64) NOT NULL UNIQUE,    -- 外部任务ID（通常UUID/雪花）
    task_name           VARCHAR(128),                   -- 任务名称（例如 DOCX_INGEST）
    status              VARCHAR(16) NOT NULL,           -- RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED
    total_docs          INTEGER DEFAULT 0,              -- 待处理文档数
    success_docs        INTEGER DEFAULT 0,              -- 成功文档数
    fail_docs           INTEGER DEFAULT 0,              -- 失败文档数
    error_message       TEXT,                           -- 失败原因摘要
    started_at          TIMESTAMPTZ,                    -- 开始时间
    finished_at         TIMESTAMPTZ,                    -- 结束时间
    created_at          TIMESTAMPTZ DEFAULT NOW(),      -- 创建时间
    updated_at          TIMESTAMPTZ DEFAULT NOW()       -- 更新时间（触发器维护）
);

-- =========================================================
-- 4) 触发器函数
-- ---------------------------------------------------------
-- kb_doc_touch_trigger_fn:
-- - 通用更新时间维护函数。
--
-- kb_chunk_tsv_trigger_fn:
-- - 自动把 content 转为 content_tsv，避免业务层手动维护 tsvector。
-- - 使用 jiebaqry 分词，必须与查询侧配置保持一致。
-- =========================================================
CREATE OR REPLACE FUNCTION kb_doc_touch_trigger_fn()
RETURNS trigger AS
$$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION kb_chunk_tsv_trigger_fn()
RETURNS trigger AS
$$
BEGIN
    NEW.content_tsv := to_tsvector('jiebaqry', COALESCE(NEW.content, ''));
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- 5) 触发器绑定
-- =========================================================
CREATE TRIGGER kb_doc_touch_trigger
BEFORE UPDATE ON kb_doc
FOR EACH ROW
EXECUTE FUNCTION kb_doc_touch_trigger_fn();

CREATE TRIGGER kb_chunk_tsv_trigger
BEFORE INSERT OR UPDATE OF content ON kb_chunk
FOR EACH ROW
EXECUTE FUNCTION kb_chunk_tsv_trigger_fn();

CREATE TRIGGER kb_ingest_task_touch_trigger
BEFORE UPDATE ON kb_ingest_task
FOR EACH ROW
EXECUTE FUNCTION kb_doc_touch_trigger_fn();

-- =========================================================
-- 6) 索引策略
-- ---------------------------------------------------------
-- kb_doc:
-- - status/hash/metadata：支持管理侧筛选与去重查询。
--
-- kb_chunk:
-- - doc_id：回表与聚合常用。
-- - content_tsv(GIN)：关键词检索主索引。
-- - embedding(ivfflat)：向量召回主索引（需 ANALYZE 且有足量数据后效果更好）。
-- - metadata(GIN)：可用于业务标签过滤。
--
-- kb_ingest_task:
-- - status：任务看板/调度器筛选。
-- =========================================================
CREATE INDEX idx_kb_doc_status ON kb_doc(status);
CREATE INDEX idx_kb_doc_hash ON kb_doc(content_hash);
CREATE INDEX idx_kb_doc_metadata ON kb_doc USING GIN(metadata);

CREATE INDEX idx_kb_chunk_doc_id ON kb_chunk(doc_id);
CREATE INDEX idx_kb_chunk_tsv ON kb_chunk USING GIN(content_tsv);
CREATE INDEX idx_kb_chunk_metadata ON kb_chunk USING GIN(metadata);
CREATE INDEX idx_kb_chunk_embedding_ivfflat ON kb_chunk
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

CREATE INDEX idx_kb_ingest_task_status ON kb_ingest_task(status);

-- =========================================================
-- 使用提示（上线前建议）：
-- 1) 将 DROP 语句替换为迁移脚本（Flyway/Liquibase）。
-- 2) embedding 维度要与实际模型完全一致（例如 1024/1536/768）。
-- 3) 大批量导入后执行 VACUUM ANALYZE 提升检索计划质量。
-- 4) 若数据规模很大，可评估 hnsw 索引替代 ivfflat。
-- =========================================================
DROP INDEX IF EXISTS idx_kb_chunk_embedding_ivfflat;

ALTER TABLE kb_chunk
    ALTER COLUMN embedding TYPE vector(1024);

CREATE INDEX idx_kb_chunk_embedding_ivfflat
    ON kb_chunk
        USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);