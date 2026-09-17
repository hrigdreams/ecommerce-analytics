package com.ecommerce.analytics.repository;

import com.ecommerce.analytics.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
