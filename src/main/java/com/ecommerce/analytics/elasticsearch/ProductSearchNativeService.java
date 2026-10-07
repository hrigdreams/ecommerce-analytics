package com.ecommerce.analytics.elasticsearch;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.ecommerce.analytics.dto.search.CategoryBucket;
import com.ecommerce.analytics.dto.search.PriceRangeBucket;
import com.ecommerce.analytics.dto.search.ProductSearchRequest;
import com.ecommerce.analytics.dto.search.ProductSearchResponse;
import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Native (Elasticsearch Java API Client) search: multi-match full-text
 * relevance search plus bucket aggregations in one request. Read-only;
 * never writes to Elasticsearch, Postgres, or any Spark output table.
 * Additive alongside ProductSearchService (CriteriaQuery-based) - does not
 * replace it.
 */
@Service
public class ProductSearchNativeService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final String CATEGORY_BUCKETS = "category_buckets";
    private static final String PRICE_RANGE_BUCKETS = "price_range_buckets";

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchNativeService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public ProductSearchResponse search(ProductSearchRequest request) {
        if (request.minPrice() != null && request.maxPrice() != null && request.minPrice() > request.maxPrice()) {
            throw new IllegalArgumentException(
                    "minPrice (" + request.minPrice() + ") must not be greater than maxPrice (" + request.maxPrice() + ")");
        }

        int safePage = Math.max(request.page(), 0);
        int safeSize = Math.min(Math.max(request.size(), 1), MAX_PAGE_SIZE);

        Query mainQuery = buildMultiMatchQuery(request.q());
        BoolQuery.Builder bool = new BoolQuery.Builder().must(mainQuery);

        if (request.categoryId() != null) {
            bool.filter(Query.of(q -> q.term(t -> t.field("categoryId").value(request.categoryId()))));
        }
        if (request.minPrice() != null || request.maxPrice() != null) {
            bool.filter(Query.of(q -> q.range(r -> {
                r.number(n -> {
                    n.field("price");
                    if (request.minPrice() != null) n.gte(request.minPrice());
                    if (request.maxPrice() != null) n.lte(request.maxPrice());
                    return n;
                });
                return r;
            })));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(bool.build()._toQuery())
                .withAggregation(CATEGORY_BUCKETS, categoryTermsAggregation())
                .withAggregation(PRICE_RANGE_BUCKETS, priceRangeAggregation())
                .withPageable(PageRequest.of(safePage, safeSize))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) hits.getTotalHits() / safeSize);

        return new ProductSearchResponse(
                results,
                hits.getTotalHits(),
                totalPages,
                extractCategoryBuckets(hits),
                extractPriceRangeBuckets(hits)
        );
    }

    /** No `q` given -> match-all, so filters/aggregations still work over the whole catalog. */
    private Query buildMultiMatchQuery(String q) {
        if (q == null || q.isBlank()) {
            return Query.of(builder -> builder.matchAll(m -> m));
        }
        return Query.of(builder -> builder.multiMatch(mm -> mm
                .query(q)
                .fields(List.of(
                        "name^3",
                        "categoryName^2"
                        // "description^1" -- uncomment once ProductDocument/Product actually have this field
                ))
                .fuzziness("AUTO")
        ));
    }

    private Aggregation categoryTermsAggregation() {
        return Aggregation.of(a -> a.terms(t -> t.field("categoryId").size(50)));
    }

    private Aggregation priceRangeAggregation() {
        return Aggregation.of(a -> a.range(r -> r
                .field("price")
                .ranges(
                        AggregationRange.of(ar -> ar.key("Under $50").to(50.0)),
                        AggregationRange.of(ar -> ar.key("$50-$200").from(50.0).to(200.0)),
                        AggregationRange.of(ar -> ar.key("Over $200").from(200.0))
                )
        ));
    }

    private List<CategoryBucket> extractCategoryBuckets(SearchHits<ProductDocument> hits) {
        return findAggregate(hits, CATEGORY_BUCKETS)
                .map(aggregate -> aggregate.lterms().buckets().array().stream()
                        .map(this::toCategoryBucket)
                        .toList())
                .orElseGet(List::of);
    }

    private CategoryBucket toCategoryBucket(LongTermsBucket bucket) {
        return new CategoryBucket(bucket.key(), bucket.docCount());
    }

    private List<PriceRangeBucket> extractPriceRangeBuckets(SearchHits<ProductDocument> hits) {
        return findAggregate(hits, PRICE_RANGE_BUCKETS)
                .map(aggregate -> aggregate.range().buckets().array().stream()
                        .map(this::toPriceRangeBucket)
                        .toList())
                .orElseGet(List::of);
    }

    private PriceRangeBucket toPriceRangeBucket(RangeBucket bucket) {
        return new PriceRangeBucket(bucket.key(), bucket.docCount());
    }

    private Optional<Aggregate> findAggregate(SearchHits<ProductDocument> hits, String name) {
        if (!(hits.getAggregations() instanceof ElasticsearchAggregations aggregations)) {
            return Optional.empty();
        }
        ElasticsearchAggregation aggregation = aggregations.get(name);
        return aggregation == null ? Optional.empty() : Optional.of(aggregation.aggregation().getAggregate());
    }
}