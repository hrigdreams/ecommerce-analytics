package com.ecommerce.analytics.elasticsearch;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Native (Elasticsearch Java API Client) search: multi-match full-text
 * relevance search plus bucket aggregations in one request. Read-only;
 * never writes to Elasticsearch, Postgres, or any Spark output table.
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

        // NEW: Wrap the base boolean query in our business ranking function_score
        Query finalQuery = buildFunctionScoreQuery(bool.build()._toQuery());

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(finalQuery)
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

    private Query buildMultiMatchQuery(String q) {
        if (q == null || q.isBlank()) {
            return Query.of(builder -> builder.matchAll(m -> m));
        }
        return Query.of(builder -> builder.multiMatch(mm -> mm
                .query(q)
                .fields(List.of(
                        "name^3",
                        "categoryName^2"
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

    /**
     * Wraps the base BM25 text query with business ranking signals (Sales, Freshness, Inventory).
     */
    /**
     * Wraps the base BM25 text query with business ranking signals (Sales & Inventory).
     * Uses Nested Function Scores to guarantee safe math without relying on Painless Scripts.
     */
    private Query buildFunctionScoreQuery(Query baseQuery) {

        // ----------------------------------------------------------------
        // INNER QUERY: BM25 * (1.0 + log1p(sales))
        // ----------------------------------------------------------------
        java.util.List<FunctionScore> innerFunctions = new java.util.ArrayList<>();

        // 1. THE ANCHOR
        // A match_all filter that applies a flat 1.0 weight to every document.
        // This guarantees the sum never drops below 1.0, protecting the text score.
        innerFunctions.add(FunctionScore.of(f -> f
                .filter(Query.of(q -> q.matchAll(m -> m)))
                .weight(1.0)
        ));

        // 2. THE SALES BOOST
        // Adds log(1 + salesCount) to the Anchor.
        // Missing fields default to 0.0, safely handling old documents.
        innerFunctions.add(FunctionScore.of(f -> f
                .fieldValueFactor(fv -> fv
                        .field("salesCount")
                        .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                        .missing(0.0)
                )
        ));

        Query innerFunctionScore = Query.of(q -> q.functionScore(fs -> fs
                .query(baseQuery)
                .functions(innerFunctions)
                .scoreMode(FunctionScoreMode.Sum)      // Adds Anchor (1.0) + Sales Boost
                .boostMode(FunctionBoostMode.Multiply) // Multiplies the Sum by the BM25 text score
        ));

        // ----------------------------------------------------------------
        // OUTER QUERY: InnerScore * (0.1 if out of stock)
        // ----------------------------------------------------------------
        java.util.List<FunctionScore> outerFunctions = new java.util.ArrayList<>();

        // 3. OUT-OF-STOCK PENALTY
        outerFunctions.add(FunctionScore.of(f -> f
                .filter(Query.of(q -> q.term(t -> t.field("inStock").value(false))))
                .weight(0.1) // Slashes the score by 90%
        ));

        return Query.of(q -> q.functionScore(fs -> fs
                .query(innerFunctionScore)
                .functions(outerFunctions)
                .scoreMode(FunctionScoreMode.Multiply)
                .boostMode(FunctionBoostMode.Multiply)
        ));
    }
}