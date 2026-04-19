
-- 启动命令
docker-compose up -d


-- 1. 激活向量检索插件
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 激活结巴中文分词插件
CREATE EXTENSION IF NOT EXISTS pg_jieba;

-- 使用 jieba 搜索模式查看分词结果
SELECT * FROM to_tsvector('jiebaqry', '赛博冰箱助手真的太好用了');

-- 创建一个测试表
CREATE TABLE test_vector (id serial PRIMARY KEY, embedding vector(3));

-- 插入几条测试数据
INSERT INTO test_vector (embedding) VALUES ('[1,2,3]'), ('[4,5,6]'), ('[1,1,1]');

-- 寻找与 [1,2,2] 最相似的一条数据（使用 L2 距离）
SELECT * FROM test_vector ORDER BY embedding <-> '[1,2,2]' LIMIT 1;


-- =========================
-- 0) 扩展与分词配置检查
-- =========================
CREATE EXTENSION IF NOT EXISTS vector;
-- pg_jieba 的扩展名/安装方式在不同环境可能略有差异，这里仅做可见性检查
SELECT extname FROM pg_extension WHERE extname IN ('vector', 'pg_jieba', 'jieba');

-- 查看可用文本搜索配置（确认是否有 jieba 相关配置）
SELECT cfgname FROM pg_ts_config ORDER BY cfgname;

-- 如果你的环境里已有 jieba 配置名（例如 jieba），后续 SQL 把 'jieba' 替换成你的实际配置名
-- 如果没有，你需要先按 pg_jieba 文档创建配置


-- ============================================
-- PostgreSQL + pgvector + pg_jieba 自测脚本
-- 你的环境配置：
--   ts_config: jiebacfg / jiebahmm / jiebamp / jiebaqry
--   extension: vector / pg_jieba
-- ============================================

-- 0) 基础检查（可重复执行）
CREATE EXTENSION IF NOT EXISTS vector;
-- pg_jieba 一般已装好，这里仅检查
SELECT extname
FROM pg_extension
WHERE extname IN ('vector', 'pg_jieba', 'jieba');

SELECT cfgname
FROM pg_ts_config
WHERE cfgname IN ('jiebacfg', 'jiebahmm', 'jiebamp', 'jiebaqry')
ORDER BY cfgname;


-- 1) 测试表（知识库）
DROP TABLE IF EXISTS kb_doc;
CREATE TABLE kb_doc (
                        id           BIGSERIAL PRIMARY KEY,
                        doc_id       VARCHAR(64) UNIQUE NOT NULL,
                        title        TEXT NOT NULL,
                        content      TEXT NOT NULL,
    -- 示例使用 4 维向量，生产改为你的 embedding 维度
                        embedding    vector(4) NOT NULL,

    -- 用 jiebacfg 生成索引字段（中文分词）
                        content_tsv  tsvector GENERATED ALWAYS AS (
                            to_tsvector('jiebaqry', coalesce(title, '') || ' ' || coalesce(content, ''))
                            ) STORED,

                        created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2) 索引
-- 2.1 向量索引（余弦）
CREATE INDEX kb_doc_embedding_ivfflat_idx
    ON kb_doc USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 2.2 关键词索引（GIN）
CREATE INDEX kb_doc_tsv_gin_idx
    ON kb_doc USING GIN (content_tsv);


-- 3) 测试数据
INSERT INTO kb_doc (doc_id, title, content, embedding) VALUES
                                                           ('DOC_001', '红烧狮子头做法', '红烧狮子头通常不是辣口，重点是肉丸鲜嫩、酱汁浓郁。', '[0.90,0.10,0.20,0.10]'),
                                                           ('DOC_002', '麻婆豆腐做法',   '麻婆豆腐是典型川菜，特点是麻、辣、烫、香。',       '[0.10,0.95,0.10,0.20]'),
                                                           ('DOC_003', '番茄炒蛋做法',   '番茄炒蛋口味偏酸甜，适合家常快手。',               '[0.80,0.20,0.20,0.10]'),
                                                           ('DOC_004', '清炖狮子头',     '清炖狮子头偏清淡，不辣，汤头鲜。',                   '[0.88,0.12,0.18,0.10]');


-- 4) 单路检索自测

-- 4.1 向量检索（距离越小越相似）
SELECT
    doc_id,
    title,
    (embedding <=> '[0.89,0.11,0.20,0.10]'::vector) AS cosine_distance
FROM kb_doc
ORDER BY embedding <=> '[0.89,0.11,0.20,0.10]'::vector
LIMIT 10;

-- 4.2 关键词检索（score 越高越相关）
-- 查询端使用 jiebaqry
WITH q AS (
    SELECT plainto_tsquery('jiebacfg', '狮子头') AS tsq
)
SELECT
    doc_id,
    title,
    ts_rank_cd(content_tsv, q.tsq) AS keyword_score
FROM kb_doc, q
WHERE content_tsv @@ q.tsq
ORDER BY keyword_score DESC
LIMIT 10;

SELECT
    doc_id,
    title,
    content_tsv,
    content_tsv @@ plainto_tsquery('jiebacfg', '狮子头') AS hit_cfg,
    content_tsv @@ plainto_tsquery('jiebaqry', '狮子头') AS hit_qry
FROM kb_doc;


-- 5) 双路融合（RRF）自测
WITH
    vector_topk AS (
        SELECT
            id, doc_id, title, content,
            ROW_NUMBER() OVER (ORDER BY embedding <=> '[0.89,0.11,0.20,0.10]'::vector ASC) AS r_vec
        FROM kb_doc
        ORDER BY embedding <=> '[0.89,0.11,0.20,0.10]'::vector
        LIMIT 20
    ),
    keyword_topk AS (
        SELECT
            id, doc_id, title, content,
            ROW_NUMBER() OVER (
                ORDER BY ts_rank_cd(content_tsv, plainto_tsquery('jiebaqry', '狮子头 辣')) DESC
                ) AS r_kw
        FROM kb_doc
        WHERE content_tsv @@ plainto_tsquery('jiebaqry', '狮子头 辣')
        ORDER BY ts_rank_cd(content_tsv, plainto_tsquery('jiebaqry', '狮子头 辣')) DESC
        LIMIT 20
    ),
    merged AS (
        SELECT
            COALESCE(v.id, k.id)       AS id,
            COALESCE(v.doc_id, k.doc_id) AS doc_id,
            COALESCE(v.title, k.title) AS title,
            COALESCE(v.content, k.content) AS content,
            v.r_vec,
            k.r_kw
        FROM vector_topk v
                 FULL OUTER JOIN keyword_topk k ON v.id = k.id
    )
SELECT
    doc_id,
    title,
    r_vec,
    r_kw,
    (COALESCE(1.0 / (60 + r_vec), 0) + COALESCE(1.0 / (60 + r_kw), 0)) AS rrf_score
FROM merged
ORDER BY rrf_score DESC
LIMIT 10;


-- 6) 分词可视化（可选）
SELECT to_tsvector('jiebacfg', '红烧狮子头通常不是辣口，重点是肉丸鲜嫩');
SELECT plainto_tsquery('jiebaqry', '狮子头 辣');

SELECT plainto_tsquery('jiebaqry', '狮子头');

SELECT plainto_tsquery('jiebacfg', '狮子头');