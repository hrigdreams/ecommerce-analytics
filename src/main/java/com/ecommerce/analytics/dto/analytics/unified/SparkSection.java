package com.ecommerce.analytics.dto.analytics.unified;

import java.util.List;
import java.util.Map;


public record SparkSection(SectionMeta meta, List<Map<String, Object>> rows) {

    public static SparkSection of(DataSource source, com.ecommerce.analytics.repository.SparkQueryResult<Map<String, Object>> result) {
        return switch (result.status()) {
            case AVAILABLE -> new SparkSection(SectionMeta.ok(source, latestWindowEnd(result.rows())), result.rows());
            case EMPTY -> new SparkSection(SectionMeta.empty(source), List.of());
            case TABLE_MISSING -> new SparkSection(SectionMeta.unavailable(source), List.of());
        };
    }

// not touching this
    private static java.time.Instant latestWindowEnd(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return null;
        Object windowEnd = rows.get(0).get("window_end");
        if (windowEnd instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null; // batch sections (product/category/geo/revenue) have no window_end - null is correct, not an error
    }
}
