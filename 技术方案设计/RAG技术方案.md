# 赛博冰箱助手：高阶 RAG (Advanced RAG) 架构设计文档

## 1. 架构总览
本项目（`ai-super-host`）的 RAG 模块摒弃了传统的单纯向量检索，采用工业级的高阶 RAG 链路：**查询重写 (Pre-retrieval) -> PG 混合检索 (Retrieval) -> 模型重排 (Post-retrieval)**。
底层存储统一使用 PostgreSQL，无需引入 Elasticsearch 等重量级组件，在保证架构极简的同时实现极致的检索精度。

## 2. 核心链路技术选型与实现机制

### 2.1 检索前 (Pre-retrieval)：Query Rewrite (查询重写)
- **目标**：解决用户在多轮对话中口语化、指代不清（如“那换成这个呢”）的问题，提升检索的命中率。
- **技术实现**：
  - 依赖：Spring AI
  - 逻辑：在正式检索前，提取 Redis 中保存的“滑动窗口历史记录”，摘要和“当前提问”，消息级注入。
  - 动作：调用大语言模型（deepseek），强制其输出一句独立、完整、包含核心食材/烹饪实体的重写后查询语句（Search Query）。

### 2.2 检索中 (Retrieval)：PostgreSQL 混合双打 (Hybrid Search)
- **目标**：兼顾语义泛化（向量）与精准匹配（专有名词/关键词），防止大模型产生幻觉。
- **技术实现**：
  - 底座：PostgreSQL 数据库。
  - 向量引擎：使用 `pgvector` 插件实现稠密向量检索（Semantic Search），对比余弦相似度。
  - 关键字引擎：使用 PostgreSQL 原生全文检索（Full-Text Search），利用 `tsvector` 和 `tsquery` 实现稀疏检索（Keyword/BM25 equivalent）。
  - 融合策略：在 SQL 层面使用 CTE (公共表表达式) 分别查出两路结果的 Top-K，这里不清楚后续怎么处理合理符合企业规范，需要明确。

### 2.3 检索后 (Post-retrieval)：DashScope 文本重排 (Reranking)
- **目标**：利用交叉编码器（Cross-Encoder）的深度注意力机制，对初筛结果进行精准“点对点”二次打分，优中选优。
- **技术实现**：
  - 依赖：阿里云 DashScope (灵积) API。
  - 模型：使用 BGE-Reranker 系列模型
  - 逻辑：将 PG 混合检索召回的 10 条结果连同重写后的 Search Query，批量发送给 DashScope Reranker 接口。根据返回的准确率得分进行降序排列，仅截取分数最高的 Top-3 作为最终上下文（Context）。

## 3. 最终生成 (Generation)
将 Reranker 筛选出的极致 Top-3 文档，与用户的历史对话摘要拼接进最终的 Prompt 中，交由主模型（如 Qwen-Max ）生成最终的菜谱建议或风味推导结果。

---

Spring AI 对自定义 RAG 是支持的，常用有两条路：
1.
用内置 RAG 管线扩展（推荐）
•
核心入口：RetrievalAugmentationAdvisor
•
你可以按阶段替换组件（1.1.x 常见）：
◦
检索前：QueryTransformer（改写、扩展、标准化查询）
◦
检索中：DocumentRetriever（向量路、关键词路、自定义多路召回）
◦
检索后：DocumentJoiner（如 ConcatenationDocumentJoiner）+ 你自己的 rerank/过滤逻辑
◦
最终注入：QueryAugmenter（把检索结果拼到 Prompt）


2.
完全自定义 Advisor（你现在这套也属于）
•
直接实现 Advisor / BaseAdvisor（或 before/after 钩子）
•
你自己控制：
◦
pre：查询改写
◦
retrieval：并发查 PG 向量 + FTS
◦
post：融合、重排、截断
◦
generation：按你定义的消息级注入