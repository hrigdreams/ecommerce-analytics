package com.ecommerce.analytics.repository;

import java.util.List;

// sanity check
public record SparkQueryResult<T>(SparkSourceStatus status, List<T> rows) {

    public static <T> SparkQueryResult<T> available(List<T> rows) {
        return new SparkQueryResult<>(SparkSourceStatus.AVAILABLE, rows);
    }

    public static <T> SparkQueryResult<T> empty() {
        return new SparkQueryResult<>(SparkSourceStatus.EMPTY, List.of());
    }

    public static <T> SparkQueryResult<T> tableMissing() {
        return new SparkQueryResult<>(SparkSourceStatus.TABLE_MISSING, List.of());
    }

    public boolean hasData() {
        return status == SparkSourceStatus.AVAILABLE && !rows.isEmpty();
    }
}
