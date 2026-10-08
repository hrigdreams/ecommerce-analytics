package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

/**
 * CQRS Analytics Updater for Elasticsearch.
 *
 * This service performs "Partial Document Updates". It is designed to be called
 * asynchronously by a Spark/Kafka consumer when behavioral analytics change.
 * It strictly updates analytical fields (sales, popularity) WITHOUT overwriting
 * core transactional data (price, name) that might have just been updated by PostgreSQL.
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

        // 1. Create a partial document containing ONLY the fields that are being updated
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

        // If nothing was passed in to update, skip the database call
        if (partialDocument.isEmpty()) {
            return;
        }

        // 2. Build the partial update query
        UpdateQuery updateQuery = UpdateQuery.builder(productId.toString())
                .withDocument(partialDocument)
                .withDocAsUpsert(false) // Do NOT create a phantom product if the base document doesn't exist yet
                .build();

        // 3. Resolve the correct index name directly from the ProductDocument entity
        // (This respects your alias and versioning setup without hardcoding "products-v1")
        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class);

        // 4. Execute the update against Elasticsearch
        elasticsearchOperations.update(updateQuery, indexCoordinates);
    }
}