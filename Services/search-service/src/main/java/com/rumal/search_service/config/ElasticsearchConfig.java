package com.rumal.search_service.config;

import com.rumal.search_service.document.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@Slf4j
@Configuration
public class ElasticsearchConfig {

    @Bean
    public ApplicationRunner ensureIndex(ElasticsearchOperations elasticsearchOperations) {
        return args -> {
            try {
                var indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
                if (!indexOps.exists()) {
                    indexOps.createWithMapping();
                    log.info("Created Elasticsearch index for ProductDocument with mappings");
                } else {
                    log.info("Elasticsearch index for ProductDocument already exists");
                }
            } catch (Exception e) {
                log.warn("Failed to create Elasticsearch index on startup: {}. Index will be created on first use.", e.getMessage());
            }
        };
    }
}
