package com.ecommerce.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {

        return http
                .authorizeHttpRequests(auth -> auth
                        // Swagger
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Application endpoints
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/products/**",
                                "/api/v1/categories/**",
                                "/api/v1/users/**",
                                "/api/v1/carts/**",
                                "/api/v1/orders/**",
                                "/api/v1/payments/**",
                                "/api/v1/reviews/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }
}