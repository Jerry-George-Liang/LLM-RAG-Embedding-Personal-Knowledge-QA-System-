-- =====================================================
-- AI+ RAG 系统 - PostgreSQL + pgvector 初始化脚本
-- 用途：创建数据库并启用 pgvector 扩展
-- 说明：向量表 (document_embeddings) 由应用自动创建 (createTable=true)
-- =====================================================

-- 创建数据库（如果不存在）
CREATE DATABASE rag
    WITH OWNER = postgres
    ENCODING = 'UTF8'
    CONNECTION LIMIT = -1;

-- 连接到 rag 数据库
\c rag

-- 启用 pgvector 扩展（如果尚未启用）
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展安装成功
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- =====================================================
-- 注意事项：
--
-- 1. 向量表 document_embeddings 由 LangChain4j PgVectorEmbeddingStore 自动创建
--    - 应用启动时自动执行 CREATE TABLE IF NOT EXISTS
--    - 表结构包含：id, embedding (vector类型), text_segment (JSONB)
--
-- 2. 如果需要手动创建表（可选），可使用以下语句：
--    CREATE TABLE document_embeddings (
--        id VARCHAR PRIMARY KEY,
--        embedding VECTOR(1024),  -- 维度需与 EmbeddingModel 匹配
--        text_segment JSONB
--    );
--
-- 3. 向量维度说明：
--    - nomic-embed-text (Ollama): 768 维
--    - mxbai-embed-large (Ollama): 1024 维
--    - all-MiniLM-L6-v2 (ONNX): 384 维
--    - text-embedding-ada-002 (OpenAI): 1536 维
--
-- 4. 性能优化（可选）：
--    当数据量超过 10 万条时，建议创建 IVFFlat 索引：
--    CREATE INDEX ON document_embeddings USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
--
-- =====================================================

-- 显示当前数据库信息
SELECT current_database(), inet_server_addr(), inet_server_port();
