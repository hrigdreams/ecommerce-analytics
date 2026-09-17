package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    void deleteByOrderId(Long orderId);
}
