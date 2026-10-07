package com.ecommerce.analytics.elasticsearch.config;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * Creates the versioned "products-v1" index (with ProductDocument's mapping)
 * and the "products" alias pointing at it, on startup, if they don't exist
 * yet. Idempotent and safe to run again (restarts, multiple instances) -
 * see ElasticsearchIndexProperties for the reindex strategy this sets up.
 *
 * Runs on ApplicationReadyEvent rather than a static @PostConstruct/schema.sql
 * because it needs Elasticsearch to actually be reachable; if ES is down at
 * startup this logs and leaves the app running (ES is a search feature, not
 * a hard dependency the whole app needs to boot - Postgres/Kafka remain the
 * source of truth and keep working with ES unavailable).
 *
 * Compile-fix note (see the inline comment on the alias block below):
 * IndexOperations.existsAlias(String) does not exist on the Spring Data
 * Elasticsearch version actually resolved by this project's pom.xml (build
 * failure: "cannot find symbol: method existsAlias(java.lang.String)").
 * Rather than guess at whatever the correct check-method name/signature is
 * for this exact version - which this sandbox cannot verify without a real
 * build - the existence check was removed entirely. It was never load-
 * bearing: Elasticsearch's Alias API (`POST /_aliases` with an "add"
 * action) is itself idempotent - adding an alias that already points at
 * the given index is defined by Elasticsearch to succeed as a no-op, not
 * to error. So indexOps.alias(...) is now called unconditionally, which
 * preserves the exact same end state (alias -> index-v1) with one fewer
 * method call and no dependency on a Java API this sandbox could not
 * confirm exists in this project's resolved version.
 */
@Component
public class ElasticsearchIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchIndexProperties indexProperties;

    public ElasticsearchIndexInitializer(
            ElasticsearchOperations elasticsearchOperations,
            ElasticsearchIndexProperties indexProperties
    ) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.indexProperties = indexProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        String indexName = indexProperties.getProductsIndexName();
        String aliasName = indexProperties.getProductsAlias();

        try {
            IndexCoordinates coordinates = IndexCoordinates.of(indexName);
            IndexOperations indexOps = elasticsearchOperations.indexOps(coordinates);

            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping(elasticsearchOperations.indexOps(ProductDocument.class).createMapping(ProductDocument.class));
                log.info("[elasticsearch] created index {} with ProductDocument mapping", indexName);
            } else {
                log.info("[elasticsearch] index {} already exists, skipping creation", indexName);
            }

            // No existsAlias() pre-check (see class doc) - "add alias" is idempotent
            // in Elasticsearch itself, so this is safe to call unconditionally on
            // every startup; it is a no-op once the alias already points here.
            indexOps.alias(new AliasActions(
                    new AliasAction.Add(
                            AliasActionParameters.builder()
                                    .withIndices(indexName)
                                    .withAliases(aliasName)
                                    .build()
                    )
            ));
            log.info("[elasticsearch] ensured alias {} -> {}", aliasName, indexName);
        } catch (Exception e) {
            // Deliberately does not rethrow / fail startup: see class doc.
            log.warn("[elasticsearch] index/alias initialization failed - is Elasticsearch running? "
                    + "Product indexing/search will not work until this succeeds. Cause: {}", e.getMessage());
        }
    }
}
