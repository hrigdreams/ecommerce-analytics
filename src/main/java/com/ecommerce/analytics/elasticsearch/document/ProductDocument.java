package com.ecommerce.analytics.elasticsearch.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.Instant;

/**
 * Search document for one product. Indexed into the "products" ALIAS (which
 * points at a versioned index, e.g. products-v1
 */
@Document(
        indexName = "#{@elasticsearchIndexProperties.productsAlias}",
        createIndex = false
)
@Mapping(mappingPath = "mappings/product-mapping.json")
public class ProductDocument {

    /**
     * Deterministic document ID = the product's own database id. Re-indexing
     * the same product (duplicate event delivery, or a later update) always
     * targets the exact same document - never creates a duplicate. This is
     * the core of this design's idempotency story.
     */
    @Id
    private Long id;

    /**
     * "text" (analyzed, for free-text relevance matching) with a "keyword"
     * sub-field (name.keyword, exact-match, used for sorting/aggregating by
     * exact name)
     */
    @MultiField(
            mainField = @Field(type = FieldType.Text, name = "name"),
            otherFields = {@InnerField(suffix = "keyword", type = FieldType.Keyword)}
    )
    private String name;

    @Field(type = FieldType.Double)
    private Double price;

    /** Filter field, not full-text: exact category match only. */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /**
     * Denormalized from Category at indexing time (ProductCreatedEvent/
     * ProductUpdatedEvent only carry categoryId, not the name) - looked up
     * once via CategoryRepository when the event is handled. Known
     * limitation: if a category is later renamed, already-indexed products
     * keep the old name until that product is next updated or reindexed.
     */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /** Denormalized aggregate from REVIEW_* events - null until the first review. */
    @Field(type = FieldType.Double)
    private Double averageRating;

    @Field(type = FieldType.Integer)
    private Integer reviewCount;

    // ------------------------------------------------------------------------
    // BUSINESS RANKING FIELDS
    // salesCount is updated by atomic Painless +=/-= scripts
    // ------------------------------------------------------------------------

    @Field(type = FieldType.Long)
    private Long salesCount = 0L;

    @Field(type = FieldType.Double)
    private Double popularityScore = 0.0;

    @Field(type = FieldType.Boolean)
    private Boolean inStock = true;

    @Field(type = FieldType.Date)
    private Instant createdAt = Instant.now();

    // ------------------------------------------------------------------------

    /**
     * Ordering/staleness guard, an incoming
     * event only overwrites this document if its occurredAt is strictly
     * after this value, OR equal AND its eventId wins the tie-break (see
     * lastEventId).
     */
    @Field(type = FieldType.Date)
    private Instant lastEventOccurredAt;

    /**
     * Paired with lastEventOccurredAt for deterministic handling of two
     * events with an identical occurredAt (timestamp granularity collision,
     * or literally the same event redelivered). Comparing eventId
     * lexicographically as a tie-break means re-processing the exact same
     * set of events in any order always converges to the same final
     * document state, rather than "whichever one happened to be applied
     * last" being processing-order-dependent.
     */
    @Field(type = FieldType.Keyword)
    private String lastEventId;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    public ProductDocument() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Long getSalesCount() { return salesCount; }
    public void setSalesCount(Long salesCount) { this.salesCount = salesCount; }

    public Double getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Double popularityScore) { this.popularityScore = popularityScore; }

    public Boolean getInStock() { return inStock; }
    public void setInStock(Boolean inStock) { this.inStock = inStock; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastEventOccurredAt() { return lastEventOccurredAt; }
    public void setLastEventOccurredAt(Instant lastEventOccurredAt) { this.lastEventOccurredAt = lastEventOccurredAt; }

    public String getLastEventId() { return lastEventId; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
