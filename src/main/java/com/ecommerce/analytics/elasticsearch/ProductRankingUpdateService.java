package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CQRS Analytics Updater for Elasticsearch.
 *
 * This service performs "Partial Document Updates". It strictly updates
 * analytical fields (salesCount, popularityScore, inStock) WITHOUT
 * overwriting core transactional data (price, name) that might have just
 * been updated by PostgreSQL.
 *
 * Update shapes, by what they're updating and why each shape was chosen:
 *
 *   - {@link #updateRankingSignals} - a plain "set these fields" partial
 *     update, kept unchanged because NativeSearchController's manual test
 *     endpoint already calls it directly.
 *
 *   - {@link #incrementSalesCount} / {@link #decrementSalesCount} - atomic
 *     Painless script updates for the salesCount COUNTER. A counter is the
 *     one field here that is genuinely unsafe to update via a Java-side
 *     read-modify-write (two concurrent orders/cancellations for the same
 *     product would race), so these never read the document first - the
 *     whole +=/-= happens inside a single Elasticsearch script execution.
 *
 *   - {@link #setInStock} - a plain partial "set" of a single boolean.
 *     Unlike salesCount this is NOT a counter - each event carries its own
 *     complete new value (true/false), not a delta - so "last write wins"
 *     (the same semantics updateRankingSignals already uses) is correct
 *     here; there is no lost-update class of bug for a plain set of an
 *     independent value the way there is for `current + delta`.
 *
 *   - {@link #bulkSetPopularityScores} - plain partial "set" of
 *     popularityScore for many products in one Elasticsearch bulk request.
 *     Also a set, not an increment: the Spark batch job recomputes each
 *     product's popularityScore from scratch on every run (time-decayed
 *     over the FULL historical window, not a delta since last run), so
 *     "set to the newly computed absolute value" is the correct, idempotent
 *     operation - re-running the same Spark output twice converges to the
 *     same ES state, unlike an increment would.
 */
@Service
public class ProductRankingUpdateService {

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductRankingUpdateService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void updateRankingSignals(Long productId, Long salesCount, Double popularityScore, Boolean inStock) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null for ranking update");
        }

        Document partialDocument = Document.create();
        if (salesCount != null) {
            partialDocument.put("salesCount", salesCount);
        }
        if (popularityScore != null) {
            partialDocument.put("popularityScore", popularityScore);
        }
        if (inStock != null) {
            partialDocument.put("inStock", inStock);
        }

        if (partialDocument.isEmpty()) {
            return;
        }

        UpdateQuery updateQuery = UpdateQuery.builder(productId.toString())
                .withDocument(partialDocument)
                .withDocAsUpsert(false)
                .build();

        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);
        elasticsearchOperations.update(updateQuery, indexCoordinates);
    }

    /**
     * Atomically adds {@code delta} to a product's salesCount. See class doc
     * for why this must be a script, not a read-modify-write.
     *
     * @param productId the product whose salesCount should be incremented
     * @param delta     amount to add (the order item's quantity); must be &gt; 0
     */
    public void incrementSalesCount(Long productId, long delta) {
        applySalesCountDelta(productId, delta, "+");
    }

    /**
     * Atomically subtracts {@code delta} from a product's salesCount - the
     * compensating transaction for an order that was counted via
     * {@link #incrementSalesCount} and is now cancelled.
     * <p>
     * Clamped at zero (`Math.max(0, current - delta)` inside the script):
     * salesCount is a "total units sold" ranking signal shown to shoppers,
     * and letting it go negative would be a visibly wrong number with no
     * real-world meaning. In the normal flow this clamp is never actually
     * exercised - ORDER_CREATED and ORDER_STATUS_CHANGED for the same order
     * are published keyed by the SAME order id (see
     * DomainEventPublisher/PaymentService's own comment: "Key by ORDER id so
     * ORDER_CREATED, ORDER_STATUS_CHANGED and PAYMENT_CREATED for one order
     * land on the same partition (in order)"), and this consumer processes
     * its single partition's messages one at a time (no `concurrency`
     * configured - see ElasticsearchIndexingConsumer's class doc), so the
     * increment for an order is always applied before any decrement for
     * that same order could ever be. The clamp is defense-in-depth for that
     * guarantee, not the primary safety mechanism.
     *
     * @param productId the product whose salesCount should be decremented
     * @param delta     amount to subtract (the cancelled order item's quantity); must be &gt; 0
     */
    public void decrementSalesCount(Long productId, long delta) {
        applySalesCountDelta(productId, delta, "-");
    }

    private void applySalesCountDelta(Long productId, long delta, String sign) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null for a ranking increment");
        }
        if (delta <= 0) {
            // Nothing to apply - not an error, just a no-op (mirrors incrementSalesCount).
            return;
        }

        String script = "+".equals(sign)
                ? "ctx._source.salesCount = (ctx._source.salesCount == null ? 0L : ctx._source.salesCount) + params.delta"
                : "ctx._source.salesCount = Math.max(0L, (ctx._source.salesCount == null ? 0L : ctx._source.salesCount) - params.delta)";

        UpdateQuery updateQuery = UpdateQuery.builder(productId.toString())
                .withScript(script)
                .withLang("painless")
                .withParams(Map.of("delta", delta))
                .withDocAsUpsert(false) // never fabricate a phantom product document
                .build();

        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);
        elasticsearchOperations.update(updateQuery, indexCoordinates);
    }

    /**
     * Sets (not toggles/not a counter) a product's inStock flag to an
     * explicit new value. Each call carries the complete new state, so a
     * duplicate/redelivered call is naturally idempotent (setting the same
     * value twice is harmless) - idempotency against actually APPLYING a
     * stale or duplicate event is the caller's job (see
     * ProductIndexingService#processProductAvailability, which guards this
     * with the existing whole-event es_processed_events key).
     *
     * @param productId the product to update
     * @param inStock   the new availability state
     */
    public void setInStock(Long productId, boolean inStock) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null for an availability update");
        }

        UpdateQuery updateQuery = UpdateQuery.builder(productId.toString())
                .withDocument(Document.from(Map.of("inStock", inStock)))
                .withDocAsUpsert(false)
                .build();

        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);
        elasticsearchOperations.update(updateQuery, indexCoordinates);
    }

    /**
     * Bulk-sets popularityScore for many products in a single Elasticsearch
     * bulk request - the mechanism {@code ProductPopularitySyncService} uses
     * to push the Spark batch job's time-decayed output into the live
     * index. A product id present in {@code scores} that does not (yet, or
     * any longer) exist as an ES document does not fail the whole request -
     * Elasticsearch bulk semantics report that as an item-level failure
     * inside the bulk response, not a request-level exception, so every
     * other product in the batch is still updated.
     * <p>
     * Known limitation (see CHANGES.md): {@code ElasticsearchOperations.bulkUpdate}
     * returns {@code void} in this project's Spring Data Elasticsearch
     * version, so this method cannot currently inspect per-item results to
     * log which specific products were skipped (e.g. one deleted between
     * the Spark run and this sync, or never backfilled). The returned count
     * below is therefore "products requested", not "products confirmed
     * updated".
     *
     * @param scores productId -> newly computed popularityScore
     * @return how many products were included in the bulk request (see limitation above)
     */
    public int bulkSetPopularityScores(Map<Long, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }

        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);

        List<UpdateQuery> queries = new ArrayList<>(scores.size());
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            queries.add(UpdateQuery.builder(entry.getKey().toString())
                    .withDocument(Document.from(Map.of("popularityScore", entry.getValue())))
                    .withDocAsUpsert(false)
                    .build());
        }

        if (queries.isEmpty()) {
            return 0;
        }

        elasticsearchOperations.bulkUpdate(queries, indexCoordinates);
        return queries.size();
    }
}
