package com.ecommerce.analytics.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SparkAnalyticsJdbcRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SparkAnalyticsJdbcRepository repository = new SparkAnalyticsJdbcRepository(jdbcTemplate);

    @Test
    void revenueTrend_shouldReportAvailableWhenRowsExist() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("day", "2026-09-01", "revenue", 100.0)));

        SparkQueryResult<Map<String, Object>> result = repository.revenueTrend(30);

        assertEquals(SparkSourceStatus.AVAILABLE, result.status());
        assertTrue(result.hasData());
        assertEquals(1, result.rows().size());
    }

    @Test
    void revenueTrend_shouldReportEmptyWhenTableExistsButHasNoRows() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        SparkQueryResult<Map<String, Object>> result = repository.revenueTrend(30);

        assertEquals(SparkSourceStatus.EMPTY, result.status());
        assertTrue(result.rows().isEmpty());
    }

    @Test
    void revenueTrend_shouldReportTableMissingWhenTableDoesNotExist_notThrow() {
        BadSqlGrammarException notYetCreated = new BadSqlGrammarException(
                "query", "SELECT * FROM spark_batch_revenue_trend ORDER BY day DESC LIMIT ?",
                new SQLException("relation \"spark_batch_revenue_trend\" does not exist"));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenThrow(notYetCreated);

        // Must NOT throw - this is the whole point: a job that hasn't run yet
        // is an ordinary state, not a failure that should break the unified endpoint.
        SparkQueryResult<Map<String, Object>> result = repository.revenueTrend(30);

        assertEquals(SparkSourceStatus.TABLE_MISSING, result.status());
        assertTrue(result.rows().isEmpty());
    }

    @Test
    void categoryPerformance_shouldQueryWithoutALimitParameter() {
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(Map.of("category_name", "Phones", "revenue", 500.0)));

        SparkQueryResult<Map<String, Object>> result = repository.categoryPerformance();

        assertEquals(SparkSourceStatus.AVAILABLE, result.status());
        assertEquals(1, result.rows().size());
    }
}
