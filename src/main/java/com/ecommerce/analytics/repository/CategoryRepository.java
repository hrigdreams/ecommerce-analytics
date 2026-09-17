package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}