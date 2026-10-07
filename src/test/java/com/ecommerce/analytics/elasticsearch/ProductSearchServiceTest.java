package com.ecommerce.analytics.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers buildCriteria's filter-combination logic directly (it's
 * package-private for exactly this reason) - the part of ProductSearchService
 * that's actually worth unit testing; the real query execution against
 * Elasticsearch is covered by the manual search verification steps instead.
 */
class ProductSearchServiceTest {

    private final ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final ProductSearchService service = new ProductSearchService(elasticsearchOperations);

    // ------------------------------------------------------------------
    // Price range validation
    // ------------------------------------------------------------------

    @Test
    void search_withMinPriceGreaterThanMaxPrice_shouldRejectBeforeQueryingElasticsearch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.search(null, null, 500.0, 100.0, null, 0, 20));

        assertTrue(ex.getMessage().contains("minPrice"));
        // Must fail fast, before ever calling Elasticsearch - not a wasted query for
        // a request that can never return a sensible result.
        verify(elasticsearchOperations, never()).search(any(CriteriaQuery.class), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withMinPriceEqualToMaxPrice_shouldBeAcceptedNotRejected() {
        SearchHits<com.ecommerce.analytics.elasticsearch.document.ProductDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.getSearchHits()).thenReturn(java.util.List.of());
        when(emptyHits.getTotalHits()).thenReturn(0L);
        when(elasticsearchOperations.search(any(CriteriaQuery.class), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class)))
                .thenReturn(emptyHits);

        // Equal bounds is a valid (if narrow) range, not an error - must not throw.
        service.search(null, null, 100.0, 100.0, null, 0, 20);

        verify(elasticsearchOperations).search(any(CriteriaQuery.class), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class));
    }

    // ------------------------------------------------------------------
    // Deterministic sorting / pagination (hardening fix)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void search_withoutTextQuery_shouldSortByIdOnly_forStablePagination() {
        SearchHits<com.ecommerce.analytics.elasticsearch.document.ProductDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.getSearchHits()).thenReturn(java.util.List.of());
        when(emptyHits.getTotalHits()).thenReturn(0L);
        var captor = org.mockito.ArgumentCaptor.forClass(CriteriaQuery.class);
        when(elasticsearchOperations.search(captor.capture(), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class)))
                .thenReturn(emptyHits);

        service.search(null, 1L, null, null, null, 0, 20);

        var sort = captor.getValue().getSort();
        assertTrue(sort.getOrderFor("id") != null, "expected a sort on id for deterministic pagination");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withTextQuery_shouldSortByScoreThenIdTiebreak() {
        SearchHits<com.ecommerce.analytics.elasticsearch.document.ProductDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.getSearchHits()).thenReturn(java.util.List.of());
        when(emptyHits.getTotalHits()).thenReturn(0L);
        var captor = org.mockito.ArgumentCaptor.forClass(CriteriaQuery.class);
        when(elasticsearchOperations.search(captor.capture(), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class)))
                .thenReturn(emptyHits);

        service.search("phone", null, null, null, null, 0, 20);

        var sort = captor.getValue().getSort();
        assertTrue(sort.getOrderFor("_score") != null, "expected relevance score sort when a text query is given");
        assertTrue(sort.getOrderFor("id") != null, "expected an id tiebreaker even with a text query");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_shouldPassThroughPageAndSizeUnchanged() {
        SearchHits<com.ecommerce.analytics.elasticsearch.document.ProductDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.getSearchHits()).thenReturn(java.util.List.of());
        when(emptyHits.getTotalHits()).thenReturn(123L);
        when(elasticsearchOperations.search(any(CriteriaQuery.class), org.mockito.ArgumentMatchers.eq(com.ecommerce.analytics.elasticsearch.document.ProductDocument.class)))
                .thenReturn(emptyHits);

        var response = service.search(null, null, null, null, null, 2, 50);

        assertEquals(2, response.page());
        assertEquals(50, response.size());
        assertEquals(123L, response.totalHits());
    }

    @Test
    void buildCriteria_withNoFilters_shouldMatchAll() {
        Criteria criteria = service.buildCriteria(null, null, null, null, null);
        assertEquals("id", criteria.getField().getName());
    }

    @Test
    void buildCriteria_withOnlyTextQuery_shouldMatchOnName() {
        Criteria criteria = service.buildCriteria("phone", null, null, null, null);
        assertEquals("name", criteria.getField().getName());
    }

    @Test
    void buildCriteria_withMultipleFilters_shouldNotThrowAndShouldStartWithTheTextMatch() {
        // All five filters combined - mainly guarding against an NPE/wrong-field-name
        // regression when chaining; exact query DSL output is verified manually
        // (see README "Verification steps"), not by introspecting Criteria internals
        // further than this, since that part of the API is more likely to shift
        // between Spring Data Elasticsearch versions.
        Criteria criteria = service.buildCriteria("phone", 1L, 100.0, 500.0, 4.0);

        assertEquals("name", criteria.getField().getName());
        assertTrue(criteria.getCriteriaChain().size() > 1);
    }

    @Test
    void buildCriteria_withOnlyCategoryFilter_shouldNotIncludeTextMatch() {
        Criteria criteria = service.buildCriteria(null, 1L, null, null, null);
        assertEquals("categoryId", criteria.getField().getName());
    }
}
