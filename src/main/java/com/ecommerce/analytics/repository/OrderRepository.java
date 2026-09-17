package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    List<Order> findAll();
}
