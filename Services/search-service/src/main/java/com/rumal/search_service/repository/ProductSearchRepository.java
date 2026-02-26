package com.rumal.search_service.repository;

import com.rumal.search_service.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.time.Instant;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    long deleteByUpdatedAtBefore(Instant cutoff);
}
