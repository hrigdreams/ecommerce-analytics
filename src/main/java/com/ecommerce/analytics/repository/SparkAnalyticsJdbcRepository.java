package com.ecommerce.analytics.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

// small reminder
@Repository
public class SparkAnalyticsJdbcRepository {

    private static final Logger log = LoggerFactory.getLogger(SparkAnalyticsJdbcRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public SparkAnalyticsJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

// quick note
    public SparkQueryResult<Map<String, Object>> revenueTrend(int limit) {
        return query("SELECT * FROM spark_batch_revenue_trend ORDER BY day DESC LIMIT ?", limit);
    }

// local note
    public SparkQueryResult<Map<String, Object>> topProductPerformance(int limit) {
        return query("SELECT * FROM spark_batch_product_performance ORDER BY revenue DESC LIMIT ?", limit);
    }

// quick note
    public SparkQueryResult<Map<String, Object>> categoryPerformance() {
        return query("SELECT * FROM spark_batch_category_performance ORDER BY revenue DESC", null);
    }

// local note
    public SparkQueryResult<Map<String, Object>> topGeoPerformance(int limit) {
        return query("SELECT * FROM spark_batch_geo_performance ORDER BY revenue DESC LIMIT ?", limit);
    }

// quick note
    public SparkQueryResult<Map<String, Object>> recentEventCounts(int limit) {
        return query("SELECT * FROM spark_streaming_event_counts ORDER BY window_start DESC LIMIT ?", limit);
    }

// local note
    public SparkQueryResult<Map<String, Object>> recentOrderActivity(int limit) {
        return query("SELECT * FROM spark_streaming_order_activity ORDER BY window_start DESC LIMIT ?", limit);
    }

// quick note
    public SparkQueryResult<Map<String, Object>> recentPaidRevenue(int limit) {
        return query("SELECT * FROM spark_streaming_revenue ORDER BY window_start DESC LIMIT ?", limit);
    }

    private SparkQueryResult<Map<String, Object>> query(String sql, Integer limit) {
        try {
            List<Map<String, Object>> rows = (limit != null)
                    ? jdbcTemplate.queryForList(sql, limit)
                    : jdbcTemplate.queryForList(sql);
            return rows.isEmpty() ? SparkQueryResult.empty() : SparkQueryResult.available(rows);
        } catch (DataAccessException e) {
            String message = String.valueOf(e.getMostSpecificCause() != null
                    ? e.getMostSpecificCause().getMessage() : e.getMessage());
            if (message.toLowerCase().contains("does not exist") || message.toLowerCase().contains("relation")) {
                log.debug("Spark output table not present yet ({}); reporting as unavailable, not an error.", message);
                return SparkQueryResult.tableMissing();
            }
            log.warn("Unexpected error reading Spark output table: {}", message);
            return SparkQueryResult.tableMissing();
        }
    }
}
