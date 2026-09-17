package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
