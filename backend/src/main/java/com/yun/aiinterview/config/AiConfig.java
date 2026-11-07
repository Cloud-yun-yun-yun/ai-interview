package com.yun.aiinterview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Slf4j
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        log.info("通用机器人开始创建");
        return builder.build();
    }

    @Bean("qaVectorStore")
    public VectorStore qaVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel model) {
        return PgVectorStore.builder(jdbcTemplate, model)
                .vectorTableName("vector_qa")
                .dimensions(1024)
                .initializeSchema(true)
                .build();
    }

    @Bean("ragAdvisor")
    public RetrievalAugmentationAdvisor ragAdvisor(@Qualifier("qaVectorStore") VectorStore qaVectorStore){
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(qaVectorStore)
                        .similarityThreshold(0.5)
                        .topK(5)
                        .build())
                .order(1)
                .build();
    }
}