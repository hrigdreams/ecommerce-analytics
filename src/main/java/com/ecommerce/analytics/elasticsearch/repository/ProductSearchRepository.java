package com.ecommerce.analytics.elasticsearch.repository;

import com.ecommerce.analytics.elasticsearch.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** CRUD against the "products" alias - see ProductDocument for index/alias details. */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}
