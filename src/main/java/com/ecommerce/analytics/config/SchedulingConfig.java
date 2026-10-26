package com.ecommerce.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PHASE 1, item 3 (continuous simulator pulse): enables Spring's
 * {@code @Scheduled} support app-wide. Nothing in the codebase declared
 * {@code @EnableScheduling} before this -- without it,
 * {@code LiveTrafficSimulator.pulseTick()}'s {@code @Scheduled} annotation
 * would be silently ignored and the method would simply never run.
 * <p>
 * Scoped to its own tiny config class (rather than added to an existing
 * {@code @Configuration}/the main application class) so it's easy to find
 * and reason about independently.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
