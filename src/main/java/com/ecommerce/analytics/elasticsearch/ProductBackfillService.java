package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.entity.Product;
import com.ecommerce.analytics.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 */
@Service
public class ProductBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ProductBackfillService.class);
    private static final int PAGE_SIZE = 500;

    private final ProductRepository productRepository;
    private final ProductIndexingService productIndexingService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProductBackfillService(ProductRepository productRepository, ProductIndexingService productIndexingService) {
        this.productRepository = productRepository;
        this.productIndexingService = productIndexingService;
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
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
