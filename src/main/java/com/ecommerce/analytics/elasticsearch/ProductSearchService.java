package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.dto.search.ProductSearchResponse;
import com.ecommerce.analytics.dto.search.ProductSearchResult;
import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

/**
 * The one real search use case this phase implements: text + category +
 * price range + minimum rating filtering over products, each filter
 * optional and combinable. Deliberately not a generic "query anything"
 * layer - only the fields ProductDocument actually has.
 *
 * Hardening notes (see the review that added these):
 *   - minPrice > maxPrice is rejected with IllegalArgumentException (the
 *     controller maps this to HTTP 400) rather than silently returning
 *     zero/undefined results for a request that is simply invalid.
 *   - Empty query (no filters at all) is explicitly defined: returns the
 *     full catalog, paginated - see buildCriteria's match-all fallback.
 *   - Sorting is now explicit and deterministic: relevance score (only
 *     meaningful when a text query `q` was given) first, then `id`
 *     ascending as a tiebreaker. Without an explicit tiebreaker,
 *     Elasticsearch does not guarantee a stable order for documents with
 *     identical relevance scores (e.g. every document, for a filter-only
 *     query with no `q`) - two consecutive requests for the same page
 *     could return a different document order, which corrupts pagination
 *     (a document could appear on two pages, or never appear at all).
 *     Sorting on the @Id field by name ("id") is the standard Spring Data
 *     Elasticsearch approach for this; it has not been verified against a
 *     live cluster in this environment (see the hardening report's
 *     "remaining concerns") - if it turns out the @Id field is not
 *     sortable as mapped, switch this to "name.keyword" instead, which is
 *     guaranteed sortable (see ProductDocument's multiField).
 */
@Service
public class ProductSearchService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public ProductSearchResponse search(
            String q, Long categoryId, Double minPrice, Double maxPrice, Double minRating, int page, int size
    ) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice (" + minPrice + ") must not be greater than maxPrice (" + maxPrice + ")");
        }

        Criteria criteria = buildCriteria(q, categoryId, minPrice, maxPrice, minRating);

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(page, size));
        query.addSort(buildSort(q));

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

        var results = hits.getSearchHits().stream()
                .map(hit -> ProductSearchResult.from(hit.getContent()))
                .toList();

        return new ProductSearchResponse(results, hits.getTotalHits(), page, size);
    }

    /**
     * Relevance score only means something when there is an actual text query;
     * for a filter-only/match-all search, sorting purely by "id" is both
     * deterministic AND more intuitive than an arbitrary constant score order.
     */
    private Sort buildSort(String q) {
        if (q != null && !q.isBlank()) {
            return Sort.by(Sort.Order.desc("_score"), Sort.Order.asc("id"));
        }
        return Sort.by(Sort.Order.asc("id"));
    }

    /** Package-private (not private) specifically so ProductSearchServiceTest can verify the filter-combination logic directly. */
    Criteria buildCriteria(String q, Long categoryId, Double minPrice, Double maxPrice, Double minRating) {
        Criteria criteria = null;

        if (q != null && !q.isBlank()) {
            criteria = Criteria.where("name").matches(q);
        }
        if (categoryId != null) {
            criteria = and(criteria, Criteria.where("categoryId").is(categoryId));
        }
        if (minPrice != null) {
            criteria = and(criteria, Criteria.where("price").greaterThanEqual(minPrice));
        }
        if (maxPrice != null) {
            criteria = and(criteria, Criteria.where("price").lessThanEqual(maxPrice));
        }
        if (minRating != null) {
            criteria = and(criteria, Criteria.where("averageRating").greaterThanEqual(minRating));
        }

        // Match-all fallback when no filters were given: "id exists" is true for every
        // indexed document (id is always set), so this returns the full (paginated) catalog.
        // This is the explicit, intended behavior for an empty query, not an accident.
        return criteria != null ? criteria : Criteria.where("id").exists();
    }

    private Criteria and(Criteria existing, Criteria next) {
        return existing == null ? next : existing.and(next);
    }
}
