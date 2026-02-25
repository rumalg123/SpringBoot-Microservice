package com.rumal.search_service.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.rumal.search_service.document.ProductDocument;
import com.rumal.search_service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final PopularSearchService popularSearchService;

    @Cacheable(value = "searchResults", key = "#request.q() + '-' + #request.page() + '-' + #request.size() + '-' + #request.sortBy() + '-' + #request.category() + '-' + #request.mainCategory() + '-' + #request.subCategory() + '-' + #request.brand() + '-' + #request.vendorId() + '-' + #request.minPrice() + '-' + #request.maxPrice()")
    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();

        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        // Build the bool query
        Query searchQuery = buildSearchQuery(request);

        // Wrap in function_score for popularity boosting
        Query scoredQuery = Query.of(q -> q.functionScore(fs -> fs
                .query(searchQuery)
                .functions(List.of(
                        co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore.of(f -> f
                                .fieldValueFactor(fvf -> fvf
                                        .field("soldCount")
                                        .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                                        .factor(0.5)
                                        .missing(0.0))),
                        co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore.of(f -> f
                                .fieldValueFactor(fvf -> fvf
                                        .field("viewCount")
                                        .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                                        .factor(0.1)
                                        .missing(0.0)))
                ))
                .scoreMode(FunctionScoreMode.Sum)
                .boostMode(FunctionBoostMode.Multiply)
        ));

        queryBuilder.withQuery(scoredQuery);

        // Aggregations for facets
        queryBuilder.withAggregation("categories",
                Aggregation.of(a -> a.terms(t -> t.field("categories.keyword").size(30))));
        queryBuilder.withAggregation("brands",
                Aggregation.of(a -> a.terms(t -> t.field("brandName.keyword").size(30))));
        queryBuilder.withAggregation("mainCategories",
                Aggregation.of(a -> a.terms(t -> t.field("mainCategory.keyword").size(20))));
        queryBuilder.withAggregation("priceRanges",
                Aggregation.of(a -> a.range(r -> r.field("sellingPrice").ranges(List.of(
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("0-25").from("0").to("25")),
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("25-50").from("25").to("50")),
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("50-100").from("50").to("100")),
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("100-250").from("100").to("250")),
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("250-500").from("250").to("500")),
                        co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.of(ar -> ar.key("500+").from("500"))
                )))));

        // Sorting
        applySorting(queryBuilder, request);

        // Pagination
        queryBuilder.withPageable(PageRequest.of(request.page(), request.size()));

        queryBuilder.withTimeout(Duration.ofSeconds(10));
        NativeQuery query = queryBuilder.build();
        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        // Track search term
        if (request.q() != null && !request.q().isBlank()) {
            popularSearchService.recordSearch(request.q());
        }

        // Map results
        List<SearchHit> hits = searchHits.getSearchHits().stream()
                .map(this::toSearchHit)
                .toList();

        List<FacetGroup> facets = extractFacets(searchHits);

        long totalElements = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / request.size());
        long tookMs = System.currentTimeMillis() - start;

        return new SearchResponse(hits, facets, request.page(), request.size(),
                totalElements, totalPages, request.q(), tookMs);
    }

    private Query buildSearchQuery(SearchRequest request) {
        return Query.of(q -> q.bool(b -> {
            // Must: text search
            if (request.q() != null && !request.q().isBlank()) {
                b.must(m -> m.multiMatch(mm -> mm
                        .query(request.q())
                        .fields("name^3", "name.autocomplete", "shortDescription^2",
                                "brandName^2", "categories", "sku")
                        .fuzziness("AUTO")
                        .prefixLength(1)
                        .minimumShouldMatch("75%")
                ));
            } else {
                b.must(m -> m.matchAll(ma -> ma));
            }

            // Filter: active only
            b.filter(f -> f.term(t -> t.field("active").value(true)));

            // Filter: category
            if (request.category() != null && !request.category().isBlank()) {
                b.filter(f -> f.term(t -> t.field("categories.keyword").value(request.category())));
            }

            // Filter: main category
            if (request.mainCategory() != null && !request.mainCategory().isBlank()) {
                b.filter(f -> f.term(t -> t.field("mainCategory.keyword").value(request.mainCategory())));
            }

            // Filter: sub category
            if (request.subCategory() != null && !request.subCategory().isBlank()) {
                b.filter(f -> f.term(t -> t.field("subCategories.keyword").value(request.subCategory())));
            }

            // Filter: brand
            if (request.brand() != null && !request.brand().isBlank()) {
                b.filter(f -> f.term(t -> t.field("brandName.keyword").value(request.brand())));
            }

            // Filter: vendor
            if (request.vendorId() != null) {
                b.filter(f -> f.term(t -> t.field("vendorId").value(request.vendorId().toString())));
            }

            // Filter: price range
            if (request.minPrice() != null || request.maxPrice() != null) {
                b.filter(f -> f.range(r -> r.number(n -> {
                    n.field("sellingPrice");
                    if (request.minPrice() != null) {
                        n.gte(request.minPrice().doubleValue());
                    }
                    if (request.maxPrice() != null) {
                        n.lte(request.maxPrice().doubleValue());
                    }
                    return n;
                })));
            }

            return b;
        }));
    }

    private void applySorting(NativeQueryBuilder queryBuilder, SearchRequest request) {
        String sortBy = request.sortBy() != null ? request.sortBy() : "relevance";

        switch (sortBy) {
            case "newest" -> queryBuilder.withSort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
            case "price-low" -> queryBuilder.withSort(s -> s.field(f -> f.field("sellingPrice").order(SortOrder.Asc)));
            case "price-high" -> queryBuilder.withSort(s -> s.field(f -> f.field("sellingPrice").order(SortOrder.Desc)));
            case "popularity" -> {
                queryBuilder.withSort(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
                queryBuilder.withSort(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc)));
            }
            case "best-selling" -> queryBuilder.withSort(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
            default -> {
                // "relevance" — sort by _score (default)
                if (request.q() == null || request.q().isBlank()) {
                    queryBuilder.withSort(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
                }
            }
        }
    }

    private SearchHit toSearchHit(org.springframework.data.elasticsearch.core.SearchHit<ProductDocument> hit) {
        ProductDocument doc = hit.getContent();
        List<SearchHit.VariationEntry> variations = doc.getVariations() != null
                ? doc.getVariations().stream()
                        .map(v -> new SearchHit.VariationEntry(v.getName(), v.getValue()))
                        .toList()
                : List.of();

        return new SearchHit(
                doc.getId(),
                doc.getSlug(),
                doc.getName(),
                doc.getShortDescription(),
                doc.getMainImage(),
                doc.getRegularPrice(),
                doc.getDiscountedPrice(),
                doc.getSellingPrice(),
                doc.getSku(),
                doc.getCategories() != null ? doc.getCategories() : Set.of(),
                doc.getMainCategory(),
                doc.getSubCategories() != null ? doc.getSubCategories() : Set.of(),
                doc.getBrandName(),
                doc.getVendorId(),
                variations,
                hit.getScore()
        );
    }

    private List<FacetGroup> extractFacets(SearchHits<ProductDocument> searchHits) {
        List<FacetGroup> facets = new ArrayList<>();

        if (searchHits.hasAggregations()) {
            var aggs = searchHits.getAggregations();
            if (aggs != null) {
                try {
                    var elasticAggs = (ElasticsearchAggregations) aggs;
                    Map<String, ElasticsearchAggregation> aggMap = elasticAggs.aggregationsAsMap();

                    // Categories facet
                    if (aggMap.containsKey("categories")) {
                        var catAgg = aggMap.get("categories").aggregation().sterms();
                        List<FacetBucket> buckets = catAgg.buckets().array().stream()
                                .map(b -> new FacetBucket(b.key().stringValue(), b.docCount()))
                                .toList();
                        facets.add(new FacetGroup("categories", buckets));
                    }

                    // Brands facet
                    if (aggMap.containsKey("brands")) {
                        var brandAgg = aggMap.get("brands").aggregation().sterms();
                        List<FacetBucket> buckets = brandAgg.buckets().array().stream()
                                .map(b -> new FacetBucket(b.key().stringValue(), b.docCount()))
                                .toList();
                        facets.add(new FacetGroup("brands", buckets));
                    }

                    // Main categories facet
                    if (aggMap.containsKey("mainCategories")) {
                        var mcAgg = aggMap.get("mainCategories").aggregation().sterms();
                        List<FacetBucket> buckets = mcAgg.buckets().array().stream()
                                .map(b -> new FacetBucket(b.key().stringValue(), b.docCount()))
                                .toList();
                        facets.add(new FacetGroup("mainCategories", buckets));
                    }

                    // Price ranges facet
                    if (aggMap.containsKey("priceRanges")) {
                        var priceAgg = aggMap.get("priceRanges").aggregation().range();
                        List<FacetBucket> buckets = priceAgg.buckets().array().stream()
                                .filter(b -> b.docCount() > 0)
                                .map(b -> new FacetBucket(b.key(), b.docCount()))
                                .toList();
                        facets.add(new FacetGroup("priceRanges", buckets));
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract facets: {}", e.getMessage());
                }
            }
        }

        return facets;
    }
}
