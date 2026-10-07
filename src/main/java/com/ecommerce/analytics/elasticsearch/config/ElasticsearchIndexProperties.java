package com.ecommerce.analytics.elasticsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Index naming, following a versioned-index + alias pattern:
 *   products-v1 (the real, physical index - holds the mapping/data)
 *   products    (an ALIAS pointing at products-v1)
 *
 * The application and ProductDocument only ever talk to the alias
 * ("productsAlias" below), never the versioned index name directly. That is
 * what makes a future reindex safe: build products-v2, backfill it, verify
 * it, then atomically repoint the "products" alias from v1 to v2 - the
 * application never has to change, and there is no window where the app is
 * pointed at a half-built index. See ElasticsearchIndexInitializer and
 * README "Reindex strategy" for the (currently manual) procedure.
 */
@Component
 @ConfigurationProperties(prefix = "app.elasticsearch.index")


public class ElasticsearchIndexProperties {

    private String productsAlias = "products";

    private int productsVersion = 1;



    public String getProductsAlias() {
 return productsAlias;
 }



    public void setProductsAlias(String productsAlias) {
 this.productsAlias = productsAlias;
 }

 public int getProductsVersion() {
 return productsVersion;
 }



    public void setProductsVersion(int productsVersion) {
 this.productsVersion = productsVersion;
 }



    public String getProductsIndexName() {
 return productsAlias + "-v" + productsVersion;
 }

}
