package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Added for the Elasticsearch product index's averageRating/reviewCount
     * fields (ProductIndexingService). A fresh recompute-from-source on every
     * REVIEW_CREATED/UPDATED/DELETED event is simpler and more robust than
     * maintaining an incremental delta: REVIEW_UPDATED's payload only carries
     * the NEW rating, not the old one it replaced, so a delta can't be
     * computed from the event alone without also consulting some other
     * record of the prior value. A per-product review count is small, so
     * this query is cheap - there is no need for the delta-tracking
     * complexity that would be worth it at a different scale.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    long countByProductId(Long productId);
}
