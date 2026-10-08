package com.ecommerce.analytics.elasticsearch;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class AnalyticsBackfillSimulator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsBackfillSimulator.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductRankingUpdateService rankingUpdateService;

    public AnalyticsBackfillSimulator(
            ElasticsearchOperations elasticsearchOperations,
            ProductRankingUpdateService rankingUpdateService) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.rankingUpdateService = rankingUpdateService;
    }

    @Override
    public void run(String... args) {
         //RUN ONCE: Injects random sales/popularity data into Elasticsearch
        //backfillElasticsearchAnalytics();
}

    private void backfillElasticsearchAnalytics() {
        log.info("Starting Analytics Backfill for Elasticsearch...");
        Random random = new Random();

        // Fetch up to 10,000 products from Elasticsearch
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withPageable(PageRequest.of(0, 10000))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

        int count = 0;
        for (SearchHit<ProductDocument> hit : hits) {
            Long productId = hit.getContent().getId();

            // Simulate realistic e-commerce data distribution:
            // 90% of items have low sales (0-50)
            // 10% of items are bestsellers (50-5000)
            long simulatedSales = random.nextInt(100) > 90 ? random.nextInt(5000) : random.nextInt(50);
            double simulatedPopularity = simulatedSales > 1000 ? 8.5 + (random.nextDouble() * 1.5) : random.nextDouble() * 5.0;

            // Execute partial update against Elasticsearch directly
            rankingUpdateService.updateRankingSignals(productId, simulatedSales, simulatedPopularity, true);
            count++;
        }

        log.info("Successfully backfilled analytics for {} products in Elasticsearch.", count);
    }
}