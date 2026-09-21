package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.CartItemAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemAnalyticsRepository extends JpaRepository<CartItemAnalytics, Long> {

    Optional<CartItemAnalytics> findByCartItemId(Long cartItemId);

    List<CartItemAnalytics> findByCartId(Long cartId);

    List<CartItemAnalytics> findByCartIdAndActiveTrue(Long cartId);

    long countByProductIdAndActiveTrue(Long productId);
}
