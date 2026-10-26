package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.repository.SparkAnalyticsJdbcRepository;
import com.ecommerce.analytics.repository.SparkQueryResult;
import com.ecommerce.analytics.repository.SparkSourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges SparkBatchAnalyticsJob's time-decayed popularityScore output
 * (Postgres table spark_batch_product_popularity) into the live
 * Elasticsearch product index.
 * <p>
 * This is deliberately a Postgres-to-Elasticsearch bridge living in the
 * Spring Boot app, NOT a direct Spark-to-Elasticsearch write from the
 * spark-analytics module. spark-analytics' own pom.xml documents its
 * design intent explicitly: "Spark jobs run as separate JVM processes and
 * communicate with the main application through PostgreSQL and Kafka" -
 * adding an Elasticsearch client dependency to that deliberately
 * ES-independent, dependency-isolated module would work against that
 * stated design, not extend it. The Spring app already owns the
 * Elasticsearch connection (ElasticsearchOperations, ProductRankingUpdateService)
 * and already bridges other spark_batch_
 * spark_streaming_* tables into
 * itself exactly this way (see SparkAnalyticsJdbcRepository /
        * UnifiedAnalyticsService) - this follows that same established pattern
 * rather than inventing a new one.
 * <p>
 * Not event-driven / no es_processed_events idempotency key is needed here:
        * each Spark batch run recomputes popularityScore for every product from
 * scratch (a full historical recompute, not an incremental delta - see
        * BatchAnalytics.productPopularity), so re-running this sync against the
 * same Spark output is naturally idempotent (same inputs -> same ES state),
        * the same "paged, re-runnable resync" idempotency story
 * ProductBackfillService already uses for Postgres -> Elasticsearch product
 * sync. A single-flight guard (the same shape ProductBackfillService uses)
 * still protects against two syncs running concurrently and interleaving
 * their bulk requests.
 */
        @Service
        public class ProductPopularitySyncService {

            private static final Logger log = LoggerFactory.getLogger(ProductPopularitySyncService.class);
            private static final int BATCH_SIZE = 500;

            private final SparkAnalyticsJdbcRepository sparkAnalyticsJdbcRepository;
            private final ProductRankingUpdateService productRankingUpdateService;
            private final AtomicBoolean running = new AtomicBoolean(false);

            public ProductPopularitySyncService(
                    SparkAnalyticsJdbcRepository sparkAnalyticsJdbcRepository,
                    ProductRankingUpdateService productRankingUpdateService
            ) {
                this.sparkAnalyticsJdbcRepository = sparkAnalyticsJdbcRepository;
                this.productRankingUpdateService = productRankingUpdateService;
            }

            public record SyncResult(SparkSourceStatus sourceStatus, int productsUpdated) {}

            /**
             * Reads the full spark_batch_product_popularity table and bulk-pushes it
             * into Elasticsearch in bounded-size batches (500 rows/request, matching
             * ProductBackfillService's PAGE_SIZE convention) rather than one huge
             * bulk request.
             *
             * @return AVAILABLE with the count pushed, EMPTY if the table has no
             * rows yet, or TABLE_MISSING if the Spark batch job has never run (the
             * table doesn't exist) - mirroring how every other Spark-output read in
             * this project reports absence as a status, not an exception (see
             * SparkQueryResult / UnifiedAnalyticsService's SectionMeta pattern).
             */
            public SyncResult sync() {
                if (!running.compareAndSet(false, true)) {
                    throw new IllegalStateException("A popularity sync is already running");
                }

                try {
                    SparkQueryResult<Map<String, Object>> result = sparkAnalyticsJdbcRepository.productPopularity();
                    if (!result.hasData()) {
                        log.info("[popularity-sync] spark_batch_product_popularity {} - nothing to sync", result.status());
                        return new SyncResult(result.status(), 0);
                    }

                    List<Map<String, Object>> rows = result.rows();
                    int totalUpdated = 0;
                    Map<Long, Double> batch = new LinkedHashMap<>(BATCH_SIZE);

                    for (Map<String, Object> row : rows) {
                        Long productId = asLong(row.get("product_id"));
                        Double popularityScore = asDouble(row.get("popularity_score"));
                        if (productId == null || popularityScore == null) {
                            log.warn("[popularity-sync] skipping malformed row {} (missing product_id/popularity_score)", row);
                            continue;
                        }

                        batch.put(productId, popularityScore);
                        if (batch.size() >= BATCH_SIZE) {
                            totalUpdated += productRankingUpdateService.bulkSetPopularityScores(batch);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        totalUpdated += productRankingUpdateService.bulkSetPopularityScores(batch);
                    }

                    log.info("[popularity-sync] pushed popularityScore for {} products into Elasticsearch", totalUpdated);
                    return new SyncResult(SparkSourceStatus.AVAILABLE, totalUpdated);
                } finally {
                    running.set(false);
                }
            }

            public boolean isRunning() {
                return running.get();
            }

            private static Long asLong(Object value) {
                return value instanceof Number n ? n.longValue() : null;
            }

            private static Double asDouble(Object value) {
                return value instanceof Number n ? n.doubleValue() : null;
            }
        }
