package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.CartAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CartAnalyticsRepository extends JpaRepository<CartAnalytics, Long> {

    List<CartAnalytics> findByUserId(Long userId);

    long countByActiveTrue();

    /** Active carts with items that haven't been touched since {@code staleBefore} (abandoned). */
    @Query("""
            SELECT c FROM CartAnalytics c
            WHERE c.active = true
              AND c.itemCount > 0
              AND c.lastActivityAt < :staleBefore
            """)
    List<CartAnalytics> findAbandoned(@Param("staleBefore") Instant staleBefore);

    @Query("""
            SELECT COUNT(c) FROM CartAnalytics c
            WHERE c.active = true
              AND c.itemCount > 0
              AND c.lastActivityAt < :staleBefore
            """)
    long countAbandoned(@Param("staleBefore") Instant staleBefore);
}
