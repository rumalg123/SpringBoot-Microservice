package com.rumal.search_service.service;

import com.rumal.search_service.document.ProductDocument;
import com.rumal.search_service.dto.AutocompleteResponse;
import com.rumal.search_service.dto.AutocompleteSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final PopularSearchService popularSearchService;

    @Cacheable(value = "autocomplete", key = "#prefix + '-' + #limit")
    public AutocompleteResponse autocomplete(String prefix, int limit) {
        List<AutocompleteSuggestion> suggestions = new ArrayList<>();

        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.match(mt -> mt
                                    .field("name.autocomplete")
                                    .query(prefix)
                                    .analyzer("standard")))
                            .filter(f -> f.term(t -> t.field("active").value(true)))
                    ))
                    .withSourceFilter(new FetchSourceFilter(
                            new String[]{"id", "slug", "name", "mainImage"}, null))
                    .withPageable(PageRequest.of(0, limit))
                    .withTimeout(java.time.Duration.ofSeconds(5))
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

            for (var hit : hits.getSearchHits()) {
                ProductDocument doc = hit.getContent();
                suggestions.add(new AutocompleteSuggestion(
                        doc.getName(),
                        "product",
                        doc.getId(),
                        doc.getSlug(),
                        doc.getMainImage()
                ));
            }
        } catch (Exception e) {
            log.warn("Autocomplete query failed: {}", e.getMessage());
        }

        // Add popular search suggestions matching prefix
        List<String> matchingPopular = popularSearchService.getPopularSearchesMatching(prefix);
        for (String term : matchingPopular) {
            boolean alreadyExists = suggestions.stream().anyMatch(s ->
                    s.text().equalsIgnoreCase(term));
            if (!alreadyExists) {
                suggestions.add(new AutocompleteSuggestion(term, "query", null, null, null));
            }
        }

        List<String> popularSearches = popularSearchService.getPopularSearches();

        return new AutocompleteResponse(suggestions, popularSearches);
    }
}
