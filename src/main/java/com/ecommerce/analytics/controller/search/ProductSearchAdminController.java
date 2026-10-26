package com.ecommerce.analytics.controller.search;

import com.ecommerce.analytics.dto.search.BackfillResponse;
import com.ecommerce.analytics.dto.search.PopularitySyncResponse;
import com.ecommerce.analytics.elasticsearch.ProductBackfillService;
import com.ecommerce.analytics.elasticsearch.ProductIndexingService;
import com.ecommerce.analytics.elasticsearch.ProductPopularitySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Manual admin triggers for Elasticsearch maintenance operations that
 * should be explicit, observable actions rather than side effects of
 * something else (app restart, a Kafka event). See each endpoint's own doc
 * for why it isn't automatic.
 *
 * Reindex-to-a-new-version workflow (see ElasticsearchIndexProperties /
 * ElasticsearchIndexInitializer):
 *   1. Bump app.elasticsearch.index.products-version, restart (creates the
 *      new physical index, e.g. "products-v2"; the "products" alias is left
 *      untouched, still pointing at v1).
 *   2. POST /api/v1/search/admin/reindex/products?targetIndex=products-v2
 *      - backfills the NEW index directly, without affecting what is
 *      currently live behind the "products" alias.
 *   3. Verify products-v2's contents/counts.
 *   4. Manually swap the alias (see README "Reindex strategy" - not
 *      automated by this endpoint on purpose: that is a one-way cutover a
 *      human should trigger deliberately, not a side effect of a backfill
 *      call).
 *
 * Security note: this project has no real authentication configured
 * anywhere yet (SecurityConfig permits every /api/v1/** path without
 * credentials - see SecurityConfig). Every endpoint below follows that
 * SAME existing convention, so it is consistent with the rest of the
 * project, but none of it is actually access-controlled. A genuinely
 * admin-only set of endpoints needs real authentication added first - that
 * is a prerequisite this task does not include (see "Known limitations" in
 * the hardening report).
 */
@RestController
@RequestMapping("/api/v1/search/admin")
@Tag(name = "Search Admin", description = "Manual Elasticsearch backfill/sync triggers")
public class ProductSearchAdminController {

    private final ProductBackfillService productBackfillService;
    private final ProductPopularitySyncService productPopularitySyncService;
    private final ProductIndexingService productIndexingService;

    public ProductSearchAdminController(
            ProductBackfillService productBackfillService,
            ProductPopularitySyncService productPopularitySyncService,
            ProductIndexingService productIndexingService
    ) {
        this.productBackfillService = productBackfillService;
        this.productPopularitySyncService = productPopularitySyncService;
        this.productIndexingService = productIndexingService;
    }

    @Operation(
            summary = "Backfill existing Postgres products into Elasticsearch",
            description = "Paged, idempotent re-sync. With no targetIndex, syncs the LIVE 'products' alias " +
                    "(whatever physical index it currently points at). With targetIndex set (e.g. 'products-v2'), " +
                    "backfills that physical index directly, bypassing the alias - for the versioned reindex " +
                    "workflow. Returns 409 if a backfill is already running."
    )
    @PostMapping("/reindex/products")
    public BackfillResponse reindexProducts(
            @Parameter(description = "Explicit physical index to backfill (e.g. products-v2). Omit to sync the live alias instead.")
            @RequestParam(required = false) String targetIndex
    ) {
        try {
            var result = productBackfillService.run(targetIndex);
            return new BackfillResponse(
                    result.productsIndexed(),
                    result.pagesProcessed(),
                    result.targetIndex()
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(
            summary = "Push Spark's time-decayed popularityScore into Elasticsearch",
            description = "Reads the full spark_batch_product_popularity table (written by SparkBatchAnalyticsJob) " +
                    "and bulk-updates popularityScore on the corresponding Elasticsearch product documents. " +
                    "Not automatic - run this after each Spark batch run (see spark-analytics/run-batch.sh and " +
                    "README 'Running the batch job'). Returns 409 if a sync is already running, 404-style EMPTY/" +
                    "TABLE_MISSING status (200, not an error) if the Spark job has not produced output yet."
    )
    @PostMapping("/sync/popularity")
    public PopularitySyncResponse syncPopularity() {
        try {
            var result = productPopularitySyncService.sync();
            return new PopularitySyncResponse(result.sourceStatus().name(), result.productsUpdated());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Operation(
            summary = "Manually set a product's inStock flag in Elasticsearch",
            description = "There is currently no stock/inventory field anywhere in this project's Product entity, " +
                    "ProductCreatedEvent/ProductUpdatedEvent payloads, or EventType enum (confirmed by inspection - " +
                    "see CHANGES.md), so there is no real Kafka event to automatically drive this from yet. This " +
                    "endpoint exposes the same idempotent processProductAvailability() plumbing that a future " +
                    "inventory event would call, so it is usable/testable today via an explicit admin action " +
                    "(e.g. ops marking a product out of stock) rather than left entirely unreachable. " +
                    "A caller-supplied idempotency key behaves exactly like a Kafka eventId would: calling this " +
                    "twice with the SAME key is a safe no-op the second time; omit it to always apply."
    )
    @PatchMapping("/products/{id}/availability")
    public void setProductAvailability(
            @Parameter(description = "Product id") @PathVariable Long id,
            @Parameter(description = "New availability state") @RequestParam boolean inStock,
            @Parameter(description = "Optional idempotency key; a fresh random one is used if omitted")
            @RequestParam(required = false) String idempotencyKey
    ) {
        String eventId = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        productIndexingService.processProductAvailability(eventId, id, inStock);
    }
}
