package com.ecommerce.analytics.controller.search;

import com.ecommerce.analytics.dto.search.BackfillResponse;
import com.ecommerce.analytics.elasticsearch.ProductBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manual trigger for the Postgres -> Elasticsearch backfill (see
 * ProductBackfillService). Deliberately not run automatically on startup -
 * indexing potentially tens of thousands of rows should be an explicit,
 * observable action, not a side effect of restarting the app.
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
 * credentials - see SecurityConfig). This endpoint follows that SAME
 * existing convention, so it is consistent with the rest of the project,
 * but it is not actually access-controlled. A genuinely admin-only endpoint
 * needs real authentication added first - that is a prerequisite this task
 * does not include (see "Known limitations" in the hardening report).
 */
@RestController
@RequestMapping("/api/v1/search/admin")
@Tag(name = "Search Admin", description = "Manual Elasticsearch backfill trigger")
public class ProductSearchAdminController {

    private final ProductBackfillService productBackfillService;

    public ProductSearchAdminController(ProductBackfillService productBackfillService) {
        this.productBackfillService = productBackfillService;
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
}
