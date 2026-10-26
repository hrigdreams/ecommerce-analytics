package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRankingUpdateServiceTest {

    private ElasticsearchOperations elasticsearchOperations;
    private ProductRankingUpdateService service;
    private final IndexCoordinates aliasIndex = IndexCoordinates.of("products");

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        when(elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(aliasIndex);
        service = new ProductRankingUpdateService(elasticsearchOperations);
    }

    // ------------------------------------------------------------------
    // incrementSalesCount (pre-existing - kept green)
    // ------------------------------------------------------------------

    @Test
    void incrementSalesCount_shouldIssueAnAtomicScriptUpdate_notAReadModifyWrite() {
        service.incrementSalesCount(10L, 3L);

        var captor = org.mockito.ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(captor.capture(), eq(aliasIndex));

        UpdateQuery query = captor.getValue();
        assertEquals("10", query.getId());
        assertEquals(3L, query.getParams().get("delta"));
        assertTrue(query.getScript().contains("+"));
        verify(elasticsearchOperations, never()).get(any(), any(), any(IndexCoordinates.class));
        verify(elasticsearchOperations, never()).save(any(), any(IndexCoordinates.class));
    }

    @Test
    void incrementSalesCount_withZeroOrNegativeDelta_shouldBeANoOp() {
        service.incrementSalesCount(10L, 0L);
        service.incrementSalesCount(10L, -5L);

        verify(elasticsearchOperations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void incrementSalesCount_withNullProductId_shouldThrowWithoutCallingElasticsearch() {
        assertThrows(IllegalArgumentException.class, () -> service.incrementSalesCount(null, 1L));

        verify(elasticsearchOperations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    // ------------------------------------------------------------------
    // decrementSalesCount (new - compensating transaction)
    // ------------------------------------------------------------------

    @Test
    void decrementSalesCount_shouldIssueAnAtomicScriptUpdate_withAClampAtZero() {
        service.decrementSalesCount(10L, 3L);

        var captor = org.mockito.ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(captor.capture(), eq(aliasIndex));

        UpdateQuery query = captor.getValue();
        assertEquals("10", query.getId());
        assertEquals(3L, query.getParams().get("delta"));
        // Must clamp at zero - a negative salesCount has no real-world meaning.
        assertTrue(query.getScript().contains("Math.max"));
        assertTrue(query.getScript().contains("-"));
        verify(elasticsearchOperations, never()).get(any(), any(), any(IndexCoordinates.class));
    }

    @Test
    void decrementSalesCount_withZeroOrNegativeDelta_shouldBeANoOp() {
        service.decrementSalesCount(10L, 0L);
        service.decrementSalesCount(10L, -1L);

        verify(elasticsearchOperations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void decrementSalesCount_withNullProductId_shouldThrowWithoutCallingElasticsearch() {
        assertThrows(IllegalArgumentException.class, () -> service.decrementSalesCount(null, 1L));

        verify(elasticsearchOperations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    void incrementThenDecrement_useDifferentScripts_soOneCannotAccidentallySubstituteForTheOther() {
        service.incrementSalesCount(10L, 5L);
        service.decrementSalesCount(10L, 5L);

        var captor = org.mockito.ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations, org.mockito.Mockito.times(2)).update(captor.capture(), eq(aliasIndex));

        List<UpdateQuery> calls = captor.getAllValues();
        org.junit.jupiter.api.Assertions.assertNotEquals(calls.get(0).getScript(), calls.get(1).getScript());
    }

    // ------------------------------------------------------------------
    // setInStock (new - plain set, not a counter)
    // ------------------------------------------------------------------

    @Test
    void setInStock_shouldIssueAPlainPartialDocumentSet_notAScript() {
        service.setInStock(10L, false);

        var captor = org.mockito.ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(captor.capture(), eq(aliasIndex));

        UpdateQuery query = captor.getValue();
        assertEquals("10", query.getId());
        assertEquals(Boolean.FALSE, query.getDocument().get("inStock"));
    }

    @Test
    void setInStock_withNullProductId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.setInStock(null, true));
    }

    // ------------------------------------------------------------------
    // bulkSetPopularityScores (new - Spark batch -> Elasticsearch bridge)
    // ------------------------------------------------------------------

    @Test
    void bulkSetPopularityScores_shouldIssueOneBulkUpdateCoveringEveryProduct() {
        int updated = service.bulkSetPopularityScores(Map.of(10L, 1.5, 20L, 0.0, 30L, 9.25));

        assertEquals(3, updated);
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(elasticsearchOperations).bulkUpdate(captor.capture(), eq(aliasIndex));
        assertEquals(3, captor.getValue().size());
    }

    @Test
    void bulkSetPopularityScores_withEmptyOrNullMap_shouldBeANoOp() {
        assertEquals(0, service.bulkSetPopularityScores(Map.of()));
        assertEquals(0, service.bulkSetPopularityScores(null));

        verify(elasticsearchOperations, never()).bulkUpdate(anyList(), any(IndexCoordinates.class));
    }
}
