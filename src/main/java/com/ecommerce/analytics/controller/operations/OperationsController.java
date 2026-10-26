package com.ecommerce.analytics.controller.operations;

import com.ecommerce.analytics.dto.search.BackfillResponse;
import com.ecommerce.analytics.elasticsearch.ProductBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * PHASE 3A, item 2 (data corruption - stale Elasticsearch data). A general
 * "operations" home for cross-cutting admin triggers that are not scoped to
 * one subsystem the way {@code /api/v1/search/admin/**} (Elasticsearch-only)
 * or {@code /api/v1/simulator/**} (simulator-only) are - named and placed
 * per this task's own requested path rather than folded into
 * ProductSearchAdminController, even though {@link ProductBackfillService}
 * already exposes a narrower, upsert-only resync there
 * ({@code POST /api/v1/search/admin/reindex/products}): that existing
 * endpoint deliberately never deletes the index or touches its mapping (see
 * ProductBackfillService's class doc), so it cannot fix either of the two
 * problems reported for this task - products indexed before the
 * categoryName field existed, or a live index whose field types were
 * dynamically guessed before product-mapping.json was wired in. This
 * endpoint can, by deleting and recreating the index from the mapping
 * first. See CHANGES.md "How to trigger the wipe" for the operational
 * tradeoffs (brief search downtime; a versioned zero-downtime alternative
 * already exists via the admin endpoint above).
 */
@RestController
@RequestMapping("/api/v1/operations")
@Tag(name = "Operations", description = "Cross-cutting operational triggers (Elasticsearch wipe/resync, etc.)")
public class OperationsController {

    private final ProductBackfillService productBackfillService;

    public OperationsController(ProductBackfillService productBackfillService) {
        this.productBackfillService = productBackfillService;
    }

    @Operation(
            summary = "Wipe and cleanly re-sync the Elasticsearch product index from PostgreSQL",
            description = "Deletes the live physical index behind the \"products\" alias (if it exists), "
                    + "recreates it from ProductDocument's mapping (product-mapping.json - categoryName as a "
                    + "keyword field, price as a numeric double, etc.), re-adds the alias, then does a full "
                    + "paged resync of every product currently in Postgres. Unlike "
                    + "POST /api/v1/search/admin/reindex/products (upsert-only, never deletes or recreates the "
                    + "index), this guarantees no stale/orphaned documents and a mapping that matches "
                    + "ProductDocument exactly - use this after a DataGenerator reseed, or any time the index's "
                    + "mapping itself is suspected stale/wrong. The \"products\" alias briefly points at an "
                    + "empty index while the resync runs. Returns 409 if a backfill/resync is already running."
    )
    @PostMapping("/reindex-es")
    public BackfillResponse reindexElasticsearch() {
        try {
            var result = productBackfillService.wipeAndResync();
            return new BackfillResponse(result.productsIndexed(), result.pagesProcessed(), result.targetIndex());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
