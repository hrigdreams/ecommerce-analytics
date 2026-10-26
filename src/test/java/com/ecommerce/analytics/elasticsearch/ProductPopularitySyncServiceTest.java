package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.repository.SparkAnalyticsJdbcRepository;
import com.ecommerce.analytics.repository.SparkQueryResult;
import com.ecommerce.analytics.repository.SparkSourceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductPopularitySyncServiceTest {

    private SparkAnalyticsJdbcRepository sparkAnalyticsJdbcRepository;
    private ProductRankingUpdateService productRankingUpdateService;
    private ProductPopularitySyncService service;

    @BeforeEach
    void setUp() {
        sparkAnalyticsJdbcRepository = mock(SparkAnalyticsJdbcRepository.class);
        productRankingUpdateService = mock(ProductRankingUpdateService.class);
        service = new ProductPopularitySyncService(sparkAnalyticsJdbcRepository, productRankingUpdateService);
    }

    private Map<String, Object> row(long productId, double score) {
        return Map.of("product_id", productId, "popularity_score", score, "computed_at", "2026-09-24T10:00:00Z");
    }

    @Test
    void sync_shouldPushEveryRowIntoElasticsearchInOneBatch() {
        when(sparkAnalyticsJdbcRepository.productPopularity())
                .thenReturn(SparkQueryResult.available(List.of(row(10L, 1.5), row(20L, 0.0))));
        when(productRankingUpdateService.bulkSetPopularityScores(anyMap())).thenReturn(2);

        ProductPopularitySyncService.SyncResult result = service.sync();

        assertEquals(SparkSourceStatus.AVAILABLE, result.sourceStatus());
        assertEquals(2, result.productsUpdated());
        verify(productRankingUpdateService, times(1)).bulkSetPopularityScores(anyMap());
    }

    @Test
    void sync_shouldBatchInChunksOf500() {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (long i = 1; i <= 1200; i++) {
            rows.add(row(i, i * 0.1));
        }
        when(sparkAnalyticsJdbcRepository.productPopularity()).thenReturn(SparkQueryResult.available(rows));
        when(productRankingUpdateService.bulkSetPopularityScores(anyMap()))
                .thenAnswer(inv -> ((Map<?, ?>) inv.getArgument(0)).size());

        ProductPopularitySyncService.SyncResult result = service.sync();

        assertEquals(1200, result.productsUpdated());
        // 1200 rows / 500 per batch -> 3 bulk calls (500 + 500 + 200)
        verify(productRankingUpdateService, times(3)).bulkSetPopularityScores(anyMap());
    }

    @Test
    void sync_whenTableEmpty_shouldReportEmptyWithoutCallingElasticsearch() {
        when(sparkAnalyticsJdbcRepository.productPopularity()).thenReturn(SparkQueryResult.empty());

        ProductPopularitySyncService.SyncResult result = service.sync();

        assertEquals(SparkSourceStatus.EMPTY, result.sourceStatus());
        assertEquals(0, result.productsUpdated());
        verify(productRankingUpdateService, never()).bulkSetPopularityScores(anyMap());
    }

    @Test
    void sync_whenTableMissing_shouldReportTableMissingNotThrow() {
        when(sparkAnalyticsJdbcRepository.productPopularity()).thenReturn(SparkQueryResult.tableMissing());

        ProductPopularitySyncService.SyncResult result = service.sync();

        assertEquals(SparkSourceStatus.TABLE_MISSING, result.sourceStatus());
        verify(productRankingUpdateService, never()).bulkSetPopularityScores(anyMap());
    }

    @Test
    void sync_shouldSkipMalformedRowsWithoutFailingTheWholeSync() {
        Map<String, Object> malformed = new java.util.HashMap<>();
        malformed.put("product_id", null);
        malformed.put("popularity_score", 1.0);
        when(sparkAnalyticsJdbcRepository.productPopularity())
                .thenReturn(SparkQueryResult.available(List.of(row(10L, 1.5), malformed)));
        when(productRankingUpdateService.bulkSetPopularityScores(anyMap())).thenReturn(1);

        ProductPopularitySyncService.SyncResult result = service.sync();

        assertEquals(1, result.productsUpdated());
    }

    @Test
    void sync_shouldRejectAConcurrentSecondRun() {
        when(sparkAnalyticsJdbcRepository.productPopularity()).thenAnswer(invocation -> {
            assertThrows(IllegalStateException.class, () -> service.sync());
            return SparkQueryResult.empty();
        });

        service.sync();
    }
}
