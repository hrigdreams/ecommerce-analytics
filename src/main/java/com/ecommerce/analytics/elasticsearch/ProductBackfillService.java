package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.config.ElasticsearchIndexProperties;
import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Indexes EXISTING Postgres products into Elasticsearch - a one-time (or
 * re-runnable) import, entirely separate from normal Kafka event ingestion
 * (ElasticsearchIndexingConsumer). Triggered explicitly via
 * POST /api/v1/search/admin/reindex/products (ProductSearchAdminController),
 * never automatically on startup - indexing potentially tens of thousands of
 * rows is not something that should happen silently just because the app
 * restarted.
 *
 * Index targeting (hardening fix): run() targets the "products" ALIAS -
 * whatever physical index is currently live - matching its original
 * behavior (keep the live index in sync with Postgres). run(String
 * targetIndexName) targets an EXPLICIT physical index by name (e.g.
 * "products-v2"), bypassing the alias entirely - this is what the
 * products-v2 -> backfill -> verify -> alias switch reindex procedure
 * needs: populating the new index must not touch whatever "products"
 * currently points at. Both paths share the same idempotent
 * upsert/dedupe/ordering logic in ProductIndexingService.
 *
 * Safety properties:
 *   - Paged (default 500 rows/page), not one unbounded query - bounded
 *     memory regardless of catalog size.
 *   - Idempotent - each row goes through
 *     ProductIndexingService.upsertProduct with a FRESH random eventId per
 *     backfill run. This means re-running the backfill is safe (every
 *     product is simply re-upserted to its current Postgres state) but does
 *     NOT dedupe against a previous backfill run the way Kafka event
 *     replay would - that is intentional: a backfill is meant to make the
 *     index match Postgres right now, not skip rows because they were
 *     already indexed once.
 *   - Single-threaded, sequential pages - deliberately not parallelized.
 *     This project's catalog sizes (see scripts/data-generator, "large" =
 *     5,000 products) do not need concurrent backfill; added complexity
 *     wasn't justified for the actual scale here.
 *   - Observable: logs progress every page and a final summary count.
 *   - Only one backfill runs at a time (see "already running" check) -
 *     prevents two concurrent runs from both hammering Elasticsearch.
 *
 * PHASE 3A addition (data corruption item 2 - "stale ES data"): {@link #wipeAndResync()}.
 * The methods above are upsert-only - they overwrite each product's
 * document by its deterministic id, but they never delete a document, and
 * they never touch the index's own mapping/schema, because the index
 * already exists and is left as-is. That is correct for keeping a healthy
 * index in sync, but it cannot fix two different classes of staleness that
 * had already happened before this task:
 *   (a) orphaned documents for products no longer in Postgres (deleted
 *       rows, or ids reused after a --reset reseed) are never cleaned up
 *       by an upsert-only resync - they just sit there forever; and
 *   (b) if the live index was ever created before product-mapping.json /
 *       the categoryName field existed (dynamic mapping guessed field
 *       types from whatever document happened to be indexed first), an
 *       upsert cannot retroactively fix a wrong field TYPE already baked
 *       into the index (e.g. price having been dynamically mapped as
 *       "text" instead of a numeric type) - only deleting and recreating
 *       the index from ProductDocument's actual mapping can.
 * wipeAndResync() deletes the live physical index behind the "products"
 * alias, recreates it from ProductDocument's mapping (identical to what
 * ElasticsearchIndexInitializer does on a cold start), re-adds the alias,
 * then runs the exact same paged upsert loop as run() against it - so the
 * result is guaranteed byte-for-byte consistent with current Postgres, with
 * no leftover documents and a clean, mapping-file-defined schema. See
 * CHANGES.md for how to trigger this and the downtime/safety tradeoffs.
 */
@Service
public class ProductBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ProductBackfillService.class);
    private static final int PAGE_SIZE = 500;

    private final ProductRepository productRepository;
    private final ProductIndexingService productIndexingService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchIndexProperties indexProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProductBackfillService(
            ProductRepository productRepository,
            ProductIndexingService productIndexingService,
            ElasticsearchOperations elasticsearchOperations,
            ElasticsearchIndexProperties indexProperties
    ) {
        this.productRepository = productRepository;
        this.productIndexingService = productIndexingService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexProperties = indexProperties;
    }

    public record BackfillResult(int productsIndexed, int pagesProcessed, String targetIndex) {}

    /** Targets the live "products" alias - keeps the current index in sync with Postgres. */
    public BackfillResult run() {
        return run(null);
    }

    /**
     * @param targetIndexName explicit physical index name (e.g. "products-v2") to
     *                         backfill into, bypassing the alias. Null means "use the
     *                         alias" (the normal/default resync case - see run()).
     */
    public BackfillResult run(String targetIndexName) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A product backfill is already running");
        }
        try {
            return doBackfill(targetIndexName);
        } finally {
            running.set(false);
        }
    }

    /**
     * PHASE 3A (data corruption item 2): completely wipes the live physical
     * index behind the "products" alias, recreates it clean from
     * ProductDocument's mapping, re-adds the alias, then does a full resync
     * from Postgres. See the class doc above for why this is a different,
     * stronger operation than {@link #run()}.
     * <p>
     * This briefly makes the "products" alias point at an empty index while
     * the resync runs (searches during that window return few/no results
     * rather than an error - see CHANGES.md "trigger the wipe" for the
     * zero-downtime alternative using a versioned index instead).
     */
    public BackfillResult wipeAndResync() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A product backfill is already running");
        }
        try {
            String indexName = indexProperties.getProductsIndexName();
            String aliasName = indexProperties.getProductsAlias();
            IndexCoordinates coordinates = IndexCoordinates.of(indexName);
            IndexOperations indexOps = elasticsearchOperations.indexOps(coordinates);

            log.warn("[es-backfill] WIPE requested: deleting index {} (if it exists) and recreating it clean "
                    + "from ProductDocument's mapping before a full resync from Postgres", indexName);

            if (indexOps.exists()) {
                indexOps.delete();
                log.warn("[es-backfill] deleted index {}", indexName);
            } else {
                log.info("[es-backfill] index {} did not exist yet - nothing to delete", indexName);
            }

            indexOps.create();
            indexOps.putMapping(
                    elasticsearchOperations.indexOps(ProductDocument.class).createMapping(ProductDocument.class)
            );
            indexOps.alias(new AliasActions(
                    new AliasAction.Add(
                            AliasActionParameters.builder()
                                    .withIndices(indexName)
                                    .withAliases(aliasName)
                                    .build()
                    )
            ));
            log.info("[es-backfill] index {} recreated clean, alias {} restored - starting full resync",
                    indexName, aliasName);

            return doBackfill(null);
        } finally {
            running.set(false);
        }
    }

    /**
     * Shared paged upsert loop. Callers MUST already hold the `running`
     * guard (both run() and wipeAndResync() acquire it themselves before
     * calling this) - this method does not touch `running` itself, so that
     * wipeAndResync() can do its delete/recreate step and then call this
     * without releasing the lock in between and letting a second caller in.
     */
    private BackfillResult doBackfill(String targetIndexName) {
        IndexCoordinates target = targetIndexName != null ? IndexCoordinates.of(targetIndexName) : null;
        AtomicInteger indexed = new AtomicInteger();
        int pageNumber = 0;
        Instant startedAt = Instant.now();
        Page<Product> page;

        log.info("[es-backfill] starting product backfill (page size {}, target {})",
                PAGE_SIZE, targetIndexName != null ? targetIndexName : "products (alias)");

        do {
            page = productRepository.findAll(PageRequest.of(pageNumber, PAGE_SIZE));

            for (Product product : page.getContent()) {
                // Fresh eventId per row: this is a resync-to-current-state operation,
                // not a replay of a specific historical event - see class doc.
                String eventId = UUID.randomUUID().toString();
                Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;

                if (target != null) {
                    productIndexingService.upsertProduct(
                            eventId, product.getId(), product.getName(), product.getPrice(),
                            categoryId, startedAt, target
                    );
                } else {
                    productIndexingService.upsertProduct(
                            eventId, product.getId(), product.getName(), product.getPrice(),
                            categoryId, startedAt
                    );
                }
                indexed.incrementAndGet();
            }

            log.info("[es-backfill] page {}/{} done ({} products so far)",
                    pageNumber + 1, page.getTotalPages(), indexed.get());
            pageNumber++;
        } while (page.hasNext());

        log.info("[es-backfill] finished: {} products indexed across {} page(s), target {}",
                indexed.get(), pageNumber, targetIndexName != null ? targetIndexName : "products (alias)");
        return new BackfillResult(indexed.get(), pageNumber, targetIndexName != null ? targetIndexName : "products");
    }

    public boolean isRunning() {
        return running.get();
    }
}
