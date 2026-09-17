package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    boolean existsByProductId(Long productId);
}