# AI+1 Project Memory

## Project Overview
- RAG 文档问答系统，基于 LangChain4j + Spring Boot + Vue 3 + TypeScript
- 后端: Java 21, Spring Boot 3.2.5, LangChain4j 0.35.0
- 前端: Vue 3, TypeScript, Element Plus, Vite
- Embedding: Ollama (mxbai-embed-large)
- LLM: DeepSeek (deepseek-v4-flash)

## Key Architecture Decisions
- **向量存储**: 2026-04-27 从 InMemoryEmbeddingStore 迁移到 PGVector (PostgreSQL + pgvector 扩展)，支持持久化
- **文件上传限制修复**: 2026-04-27 修复前端上传 20KB 限制问题
  - 根因: axios 全局 Content-Type 为 application/json，FormData 上传需浏览器自动设置 boundary
  - 修复: 拦截器中对 FormData 删除 Content-Type; 移除手动 headers; 超时 60s→300s
  - 后端补充 Tomcat max-http-form-post-size 和 max-swallow-size 配置

## Infrastructure
- Docker Compose: PostgreSQL 16 + PGVector 扩展 (pgvector/pgvector:pg16)
- PGVector 连接配置通过环境变量覆盖: PGVECTOR_HOST, PGVECTOR_PORT, PGVECTOR_DB, PGVECTOR_USER, PGVECTOR_PASSWORD
- 向量表: document_embeddings, dimension 由 EmbeddingModel 动态获取

## File Structure Notes
- VectorStoreConfig.java: 定义 EmbeddingStore<TextSegment> Bean (PGVector)
- RetrieverConfig.java: RagRetriever 使用 EmbeddingStore<TextSegment> 接口
- VectorizationService.java: 依赖 EmbeddingStore<TextSegment> 接口
- documentRegistry: ConcurrentHashMap 仍为内存级（文档元数据），考虑后续也持久化
