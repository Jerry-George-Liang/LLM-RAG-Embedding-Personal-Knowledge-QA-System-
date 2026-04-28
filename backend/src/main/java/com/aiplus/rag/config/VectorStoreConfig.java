package com.aiplus.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Value("${rag.vector.pgvector.host:localhost}")
    private String host;

    @Value("${rag.vector.pgvector.port:5432}")
    private int port;

    @Value("${rag.vector.pgvector.database:rag}")
    private String database;

    @Value("${rag.vector.pgvector.user:postgres}")
    private String user;

    @Value("${rag.vector.pgvector.password:postgres}")
    private String password;

    @Value("${rag.vector.pgvector.table:document_embeddings}")
    private String table;

    @Value("${rag.vector.pgvector.use-index:false}")
    private boolean useIndex;

    @Value("${rag.vector.pgvector.index-list-size:100}")
    private int indexListSize;

    @Value("${rag.vector.pgvector.drop-table-first:false}")
    private boolean dropTableFirst;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        int dimension = embeddingModel.dimension();
        log.info("初始化 PGVector EmbeddingStore: {}:{}/{} table={} dimension={}",
                host, port, database, table, dimension);

        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .useIndex(useIndex)
                .indexListSize(indexListSize)
                .createTable(true)
                .dropTableFirst(dropTableFirst)
                .build();

        log.info("PGVector EmbeddingStore 初始化完成");
        return store;
    }

    @Bean
    public ConcurrentHashMap<String, com.aiplus.rag.model.DocumentMetadata> documentRegistry() {
        return new ConcurrentHashMap<>();
    }
}
